/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.steps.durable_task;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.XmlFile;
import hudson.model.Computer;
import hudson.model.PeriodicWork;
import hudson.model.TaskListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.durabletask.Controller;
import org.jenkinsci.plugins.durabletask.DurableTask;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Runs an durable task on a slave, such as a shell script.
 */
public abstract class DurableTaskStep extends Step {

    private static final Logger LOGGER = Logger.getLogger(DurableTaskStep.class.getName());

    protected abstract DurableTask task();

    @Override public final boolean start(StepContext context) {
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
            register(context, task().launch(context.get(EnvVars.class), ws, context.get(Launcher.class), context.get(TaskListener.class)), node, remote);
        } catch (Exception x) {
            context.onFailure(x);
        }
        return false;
        // TODO implement stop, however it is design (will need to call Controller.stop)
    }

    protected abstract static class DurableTaskStepDescriptor extends StepDescriptor {
        
        @Override public final Set<Class<?>> getRequiredContext() {
            Set<Class<?>> r = new HashSet<Class<?>>();
            r.add(EnvVars.class);
            r.add(FilePath.class);
            r.add(Launcher.class);
            r.add(TaskListener.class);
            return r;
        }

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

    private static synchronized void check() {
        load();
        boolean changed = false;
        Iterator<RunningTask> it = runningTasks.iterator();
        while (it.hasNext()) {
            RunningTask rt = it.next();
            switch (rt.check()) {
            case UPDATED:
                changed = true;
                break;
            case DONE:
                changed = true;
                it.remove();
                break;
            default:
                // NO_CHANGE, leave in queue
            }
        }
        if (changed) {
            save();
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
                    context.onFailure(new AbortException("missing workspace " + remote + " on " + node));
                    return CheckResult.DONE;
                }
                TaskListener listener = context.get(TaskListener.class);
                boolean wrote = controller.writeLog(ws, listener.getLogger());
                Integer exitCode = controller.exitStatus(ws);
                if (exitCode == null) {
                    LOGGER.log(Level.FINE, "still running in {0} on {1}", new Object[] {remote, node});
                    return wrote ? CheckResult.UPDATED : CheckResult.NO_CHANGE;
                } else if (exitCode == 0) {
                    context.onSuccess(exitCode);
                } else {
                    context.onFailure(new AbortException("script returned exit code " + exitCode));
                }
                controller.cleanup(ws);
                return CheckResult.DONE;
            } catch (IOException x) {
                context.onFailure(x);
            } catch (InterruptedException x) {
                context.onFailure(x);
            }
            return CheckResult.DONE;
        }

    }

    @Restricted(NoExternalUse.class)
    @Extension public static final class Checker extends PeriodicWork {

        @Override public long getRecurrencePeriod() {
            return 5000; // 5s
        }

        @Override protected void doRun() throws Exception {
            check();
        }

    }

}
