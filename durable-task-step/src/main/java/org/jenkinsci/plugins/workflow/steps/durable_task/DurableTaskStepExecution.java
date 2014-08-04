package org.jenkinsci.plugins.workflow.steps.durable_task;

import com.google.common.base.Function;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
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

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents one task that is believed to still be running.
 *
 * @author Jesse Glick
 * @author Kohsuke Kawaguchi
 */
public class DurableTaskStepExecution extends StepExecution {
    private transient final DurableTask task;

    // these fields are set during the start
    private /*almost final*/ Controller controller;
    private /*almost final*/ String node;
    private /*almost final*/ String remote;

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
            controller = task.launch(context.get(EnvVars.class), ws, context.get(Launcher.class), context.get(TaskListener.class));
            this.node = node;
            this.remote = remote;
        } catch (Exception x) {
            context.onFailure(x);
        }
        return false;
    }

    private @CheckForNull FilePath getWorkspace() throws IOException, InterruptedException {
        Computer c = Jenkins.getInstance().getComputer(node);
        if (c == null) {
            LOGGER.log(Level.FINE, "no such computer {0}", node);
            return null;
        }
        if (c.isOffline()) {
            LOGGER.log(Level.FINE, "{0} is offline", node);
            return null;
        }
        FilePath ws = new FilePath(c.getChannel(), remote);
        if (!ws.isDirectory()) {
            throw new AbortException("missing workspace " + remote + " on " + node);
        }
        return ws;
    }

    @Override
    public void stop() throws Exception {
        FilePath ws = getWorkspace();
        if (ws!=null)
            controller.stop(ws);
    }

    private static void checkAll() throws ExecutionException, InterruptedException {
        StepExecution.applyAll(DurableTaskStepExecution.class,new Function<DurableTaskStepExecution, Void>() {
            @Override
            public Void apply(DurableTaskStepExecution rt) {
                rt.check();
                return null;
            }
        }).get();
    }

    /** Checks for progress or completion of the external task. */
    private void check() {
        try {
            FilePath ws = getWorkspace();
            if (ws==null) {
                return;
            }
            TaskListener listener = context.get(TaskListener.class);
            if (controller.writeLog(ws, listener.getLogger())) {
                context.saveState();
            }
            Integer exitCode = controller.exitStatus(ws);
            if (exitCode == null) {
                LOGGER.log(Level.FINE, "still running in {0} on {1}", new Object[] {remote, node});
            } else {
                controller.cleanup(ws);
                if (exitCode == 0) {
                    context.onSuccess(exitCode); // TODO could add an option to have this be text output from command
                } else {
                    context.onFailure(new AbortException("script returned exit code " + exitCode));
                }
            }
        } catch (IOException x) {
            context.onFailure(x);
        } catch (InterruptedException x) {
            context.onFailure(x);
        }
    }

    @Restricted(NoExternalUse.class)
    @Extension
    public static final class Checker extends PeriodicWork {

        @Override public long getRecurrencePeriod() {
            return 5000; // 5s
        }

        @Override protected void doRun() throws Exception {
            checkAll();
        }

    }

    private static final Logger LOGGER = Logger.getLogger(DurableTaskStepExecution.class.getName());
}
