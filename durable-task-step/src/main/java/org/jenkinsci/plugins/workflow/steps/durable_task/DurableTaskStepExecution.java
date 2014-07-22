package org.jenkinsci.plugins.workflow.steps.durable_task;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.XmlFile;
import hudson.model.Computer;
import hudson.model.PeriodicWork;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.durabletask.Controller;
import org.jenkinsci.plugins.durabletask.DurableTask;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO: {@link RunningTask} should be subsumed by {@link DurableTaskStepExecution}.
 *
 * @author Jesse Glick
 * @author Kohsuke Kawaguchi
 */
public class DurableTaskStepExecution extends StepExecution {
    private final DurableTask task;

    public DurableTaskStepExecution(StepContext context, DurableTask task) {
        super(context);
        this.task = task;
    }

    @Override
    public boolean start() throws Exception {
        try {
            FilePath ws = context.get(FilePath.class);
            assert ws != null : context.getClass() + " failed to provide a FilePath even though one was requested";
            String remote = ws.getRemote();
            String node = null;
            for (Computer c : Jenkins.getInstance().getComputers()) {
                if (c.getChannel() == ws.getChannel()) {
                    node = c.getName();
                    break;
                }
            }
            if (node == null) {
                throw new IllegalStateException("no known node for " + ws);
            }
            register(context, task.launch(context.get(EnvVars.class), ws, context.get(Launcher.class), context.get(TaskListener.class)), node, remote);
        } catch (Exception x) {
            context.onFailure(x);
        }
        return false;
        // TODO implement stop, however it is design (will need to call Controller.stop)
    }


    private static List<RunningTask> runningTasks;

    private static XmlFile getConfigFile() {
        return new XmlFile(new File(Jenkins.getInstance().getRootDir(), DurableTaskStep.class.getName() + ".xml"));
    }

    @SuppressWarnings("unchecked")
    private static synchronized void load() {
        if (runningTasks == null) {
            runningTasks = new ArrayList<RunningTask>();
            XmlFile configFile = getConfigFile();
            if (configFile.exists()) {
                try {
                    runningTasks = (List<RunningTask>) configFile.read();
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, null, x);
                }
            }
        }
    }

    private static synchronized void save() {
        try {
            getConfigFile().write(runningTasks);
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
        }
    }

    private static synchronized void register(StepContext context, Controller controller, String node, String remote) {
        load();
        runningTasks.add(new RunningTask(context, controller, node, remote));
        save();
    }

    private static void check() {
        List<RunningTask> done = new LinkedList<RunningTask>();
        synchronized (DurableTaskStep.class) {
            load();
            boolean changed = false;
            for (RunningTask rt : runningTasks) {
                switch (rt.check()) {
                case UPDATED:
                    changed = true;
                    break;
                case DONE:
                    changed = true;
                    done.add(rt);
                    break;
                default:
                    // NO_CHANGE, leave in queue
                }
            }
            if (changed) {
                runningTasks.removeAll(done);
                save();
            }
        }
        for (RunningTask rt : done) {
            rt.report();
        }
    }

    private enum CheckResult {
        /** Task still believed to be running, but has produced no new output since the last check. */
        NO_CHANGE,
        /** Task has produced new output but is still running. */
        UPDATED,
        /** Task is finished (or in an unrecoverable error state). */
        DONE
    }

    /**
     * Represents one task that is believed to still be running.
     * Serializable; one row of our state table.
     */
    private static final class RunningTask {

        private final StepContext context;
        private final Controller controller;
        private final String node;
        private final String remote;
        private transient Object result;
        private transient Throwable error;

        RunningTask(StepContext context, Controller controller, String node, String remote) {
            this.context = context;
            this.controller = controller;
            this.node = node;
            this.remote = remote;
        }

        /** Checks for progress or completion of the external task. */
        CheckResult check() {
            try {
                Computer c = Jenkins.getInstance().getComputer(node);
                if (c == null) {
                    LOGGER.log(Level.FINE, "no such computer {0}", node);
                    return CheckResult.NO_CHANGE;
                }
                if (c.isOffline()) {
                    LOGGER.log(Level.FINE, "{0} is offline", node);
                    return CheckResult.NO_CHANGE;
                }
                FilePath ws = new FilePath(c.getChannel(), remote);
                if (!ws.isDirectory()) {
                    error = new AbortException("missing workspace " + remote + " on " + node);
                    return CheckResult.DONE;
                }
                TaskListener listener = context.get(TaskListener.class);
                boolean wrote = controller.writeLog(ws, listener.getLogger());
                Integer exitCode = controller.exitStatus(ws);
                if (exitCode == null) {
                    LOGGER.log(Level.FINE, "still running in {0} on {1}", new Object[] {remote, node});
                    return wrote ? CheckResult.UPDATED : CheckResult.NO_CHANGE;
                } else if (exitCode == 0) {
                    result = exitCode; // TODO could add an option to have this be text output from command
                } else {
                    error = new AbortException("script returned exit code " + exitCode);
                }
                controller.cleanup(ws);
                return CheckResult.DONE;
            } catch (IOException x) {
                error = x;
            } catch (InterruptedException x) {
                error = x;
            }
            return CheckResult.DONE;
        }

        /** Reports success or failure of step, outside synchronization block to avoid deadlocks. */
        void report() {
            if (error != null) {
                context.onFailure(error);
            } else {
                context.onSuccess(result);
            }
        }

    }

    @Restricted(NoExternalUse.class)
    @Extension
    public static final class Checker extends PeriodicWork {

        @Override public long getRecurrencePeriod() {
            return 5000; // 5s
        }

        @Override protected void doRun() throws Exception {
            check();
        }

    }

    private static final Logger LOGGER = Logger.getLogger(DurableTaskStepExecution.class.getName());
}
