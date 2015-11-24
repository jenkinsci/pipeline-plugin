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

import com.google.inject.Inject;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.jenkinsci.plugins.durabletask.Controller;
import org.jenkinsci.plugins.durabletask.DurableTask;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    @edu.umd.cs.findbugs.annotations.SuppressWarnings("SE_TRANSIENT_FIELD_NOT_RESTORED") // recurrencePeriod is set in onResume, not deserialization
    public static final class Execution extends AbstractStepExecutionImpl implements Runnable {

        private static final long MIN_RECURRENCE_PERIOD = 250; // ¼s
        private static final long MAX_RECURRENCE_PERIOD = 15000; // 15s
        private static final float RECURRENCE_PERIOD_BACKOFF = 1.2f;

        @Inject(optional=true) private transient DurableTaskStep step;
        @StepContextParameter private transient FilePath ws;
        @StepContextParameter private transient EnvVars env;
        @StepContextParameter private transient Launcher launcher;
        @StepContextParameter private transient TaskListener listener;
        private transient long recurrencePeriod;
        private Controller controller;
        private String node;
        private String remote;

        @Override public boolean start() throws Exception {
            for (Computer c : Jenkins.getActiveInstance().getComputers()) {
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
            setupTimer();
            return false;
        }

        private @CheckForNull FilePath getWorkspace() throws AbortException {
            if (ws == null) {
                Jenkins j = Jenkins.getInstance();
                if (j == null) {
                    LOGGER.fine("Jenkins is not running");
                    return null;
                }
                Computer c = j.getComputer(node);
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
            boolean directory;
            try {
                directory = ws.isDirectory();
            } catch (Exception x) {
                // RequestAbortedException, ChannelClosedException, EOFException, wrappers thereof…
                LOGGER.log(Level.FINE, node + " is evidently offline now", x);
                ws = null;
                return null;
            }
            if (!directory) {
                throw new AbortException("missing workspace " + remote + " on " + node);
            }
            return ws;
        }

        @Override public void stop(Throwable cause) throws Exception {
            FilePath workspace = getWorkspace();
            if (workspace != null) {
                controller.stop(workspace, getContext().get(Launcher.class));
            }
        }

        /** Checks for progress or completion of the external task. */
        @Override public void run() {
            try {
                check();
            } finally {
                if (recurrencePeriod > 0) {
                    Timer.get().schedule(this, recurrencePeriod, TimeUnit.MILLISECONDS);
                }
            }
        }

        private void check() {
            FilePath workspace;
            try {
                workspace = getWorkspace();
            } catch (AbortException x) {
                recurrencePeriod = 0;
                getContext().onFailure(x);
                return;
            }
            if (workspace == null) {
                return; // slave not yet ready, wait for another day
            }
            // Do not allow this to take more than 3s for any given task:
            final AtomicReference<Thread> t = new AtomicReference<Thread>(Thread.currentThread());
            Timer.get().schedule(new Runnable() {
                @Override public void run() {
                    Thread _t = t.get();
                    if (_t != null) {
                        _t.interrupt();
                    }
                }
            }, 3, TimeUnit.SECONDS);
            try {
                if (controller.writeLog(workspace, listener.getLogger())) {
                    getContext().saveState();
                    recurrencePeriod = MIN_RECURRENCE_PERIOD; // got output, maybe we will get more soon
                } else {
                    recurrencePeriod = Math.min((long) (recurrencePeriod * RECURRENCE_PERIOD_BACKOFF), MAX_RECURRENCE_PERIOD);
                }
                Integer exitCode = controller.exitStatus(workspace, launcher);
                if (exitCode == null) {
                    LOGGER.log(Level.FINE, "still running in {0} on {1}", new Object[] {remote, node});
                } else {
                    recurrencePeriod = 0;
                    if (controller.writeLog(workspace, listener.getLogger())) {
                        LOGGER.log(Level.FINE, "last-minute output in {0} on {1}", new Object[] {remote, node});
                    }
                    controller.cleanup(workspace);
                    if (exitCode == 0) {
                        getContext().onSuccess(exitCode);
                    } else {
                        getContext().onFailure(new AbortException("script returned exit code " + exitCode));
                    }
                }
            } catch (IOException x) {
                LOGGER.log(Level.FINE, "could not check " + workspace, x);
                ws = null;
            } catch (InterruptedException x) {
                LOGGER.log(Level.FINE, "could not check " + workspace, x);
                ws = null;
            } finally {
                t.set(null); // cancel timer
            }
        }

        @Override public void onResume() {
            super.onResume();
            setupTimer();
        }

        private void setupTimer() {
            recurrencePeriod = MIN_RECURRENCE_PERIOD;
            Timer.get().schedule(this, recurrencePeriod, TimeUnit.MILLISECONDS);
        }

        private static final long serialVersionUID = 1L;

    }

}
