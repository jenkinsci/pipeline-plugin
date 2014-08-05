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

import com.google.common.base.Function;
import com.google.inject.Inject;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.PeriodicWork;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.durabletask.Controller;
import org.jenkinsci.plugins.durabletask.DurableTask;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Runs an durable task on a slave, such as a shell script.
 */
public abstract class DurableTaskStep extends AbstractStepImpl {

    private static final Logger LOGGER = Logger.getLogger(DurableTaskStep.class.getName());

    protected abstract DurableTask task();

    protected abstract static class DurableTaskStepDescriptor extends AbstractStepDescriptorImpl {
        protected DurableTaskStepDescriptor() {
            super(Execution.class);
        }
    }

    /**
     * Represents one task that is believed to still be running.
     */
    @Restricted(NoExternalUse.class)
    public static final class Execution extends StepExecution {

        @Inject private transient DurableTaskStep step;
        @StepContextParameter private transient FilePath ws;
        @StepContextParameter private transient EnvVars env;
        @StepContextParameter private transient Launcher launcher;
        @StepContextParameter private transient TaskListener listener;
        private Controller controller;
        private String node;
        private String remote;

        @Override public boolean start() throws Exception {
            for (Computer c : Jenkins.getInstance().getComputers()) {
                if (c.getChannel() == ws.getChannel()) {
                    node = c.getName();
                    break;
                }
            }
            if (node == null) {
                throw new IllegalStateException("no known node for " + ws);
            }
            controller = step.task().launch(env, ws, launcher, listener);
            this.remote = ws.getRemote();
            return false;
        }

        private @CheckForNull FilePath getWorkspace() throws IOException, InterruptedException {
            if (ws == null) {
                Computer c = Jenkins.getInstance().getComputer(node);
                if (c == null) {
                    LOGGER.log(Level.FINE, "no such computer {0}", node);
                    return null;
                }
                if (c.isOffline()) {
                    LOGGER.log(Level.FINE, "{0} is offline", node);
                    return null;
                }
                ws = new FilePath(c.getChannel(), remote);
            }
            if (!ws.isDirectory()) {
                throw new AbortException("missing workspace " + remote + " on " + node);
            }
            return ws;
        }

        @Override public void stop() throws Exception {
            FilePath workspace = getWorkspace();
            if (workspace != null) {
                controller.stop(workspace);
            }
        }

        /** Checks for progress or completion of the external task. */
        private void check() {
            try {
                FilePath workspace = getWorkspace();
                if (workspace == null) {
                    return;
                }
                if (controller.writeLog(workspace, listener.getLogger())) {
                    context.saveState();
                }
                Integer exitCode = controller.exitStatus(workspace);
                if (exitCode == null) {
                    LOGGER.log(Level.FINE, "still running in {0} on {1}", new Object[] {remote, node});
                } else {
                    controller.cleanup(workspace);
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

    }

    @Restricted(NoExternalUse.class)
    @Extension public static final class Checker extends PeriodicWork {
        @Override public long getRecurrencePeriod() {
            return 5000; // 5s
        }
        @Override protected void doRun() throws Exception {
            StepExecution.applyAll(Execution.class, new Function<Execution,Void>() {
                @Override public Void apply(Execution e) {
                    e.check();
                    return null;
                }
            }).get();
        }
    }

}
