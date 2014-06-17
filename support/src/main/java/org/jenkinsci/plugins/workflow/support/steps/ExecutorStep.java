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

package org.jenkinsci.plugins.workflow.support.steps;

import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.support.PrioritizedTask;
import com.google.common.util.concurrent.FutureCallback;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceList;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.queue.AbstractQueueTask;
import hudson.model.queue.SubTask;
import hudson.remoting.ChannelClosedException;
import hudson.remoting.RequestAbortedException;
import hudson.security.AccessControlled;
import hudson.slaves.WorkspaceList;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Grabs an {@link Executor} on a node of your choice and runs its block with that executor occupied.
 */
public final class ExecutorStep extends Step {

    private static final Logger LOGGER = Logger.getLogger(ExecutorStep.class.getName());

    private final String label;

    @DataBoundConstructor public ExecutorStep(String label) {
        this.label = label;
    }
    
    public String getLabel() {
        return label;
    }

    @Override public boolean start(StepContext context) throws Exception {
        Queue.getInstance().schedule2(new PlaceholderTask(context, label), 0);
        return false;
    }

    private static final class PlaceholderTask extends AbstractQueueTask implements PrioritizedTask, Serializable {

        /** map from cookies to contexts of tasks thought to be running */
        private static final Map<String,StepContext> runningTasks = new HashMap<String,StepContext>();

        private final StepContext context;
        private String label;
        /**
         * Unique cookie set once the task starts.
         * Serves multiple purposes:
         * identifies whether we have already invoked the body (since this can be rerun after restart);
         * serves as a key for {@link #runningTasks} and {@link Callback} (cannot just have a doneness flag in {@link PlaceholderTask} because multiple copies might be deserialized);
         * and allows {@link Launcher#kill} to work.
         */
        private String cookie;

        PlaceholderTask(StepContext context, String label) {
            this.context = context;
            this.label = label;
        }

        private Object readResolve() {
            LOGGER.log(Level.FINE, "deserialized {0}", cookie);
            if (cookie != null) {
                synchronized (runningTasks) {
                    runningTasks.put(cookie, context);
                }
            }
            return this;
        }

        @Override public Queue.Executable createExecutable() throws IOException {
            return new PlaceholderExecutable();
        }

        @Override public Label getAssignedLabel() {
            if (label == null) {
                return null;
            } else if (label.isEmpty()) {
                return Jenkins.getInstance().getSelfLabel();
            } else {
                return Label.get(label);
            }
        }

        @Override public Node getLastBuiltOn() {
            return Jenkins.getInstance().getNode(label);
        }

        @Override public boolean isBuildBlocked() {
            return false;
        }

        @Deprecated
        @Override public String getWhyBlocked() {
            return null;
        }

        // TODO not sure we need bother with all this, since there is not yet any clear linkage from Executor.interrupt to FlowExecution.abort
        private @CheckForNull AccessControlled accessControlled() {
            try {
                if (!context.isReady()) {
                    return null;
                }
                FlowExecution exec = context.get(FlowExecution.class);
                if (exec == null) {
                    return null;
                }
                Queue.Executable executable = exec.getOwner().getExecutable();
                if (executable instanceof AccessControlled) {
                    return (AccessControlled) executable;
                } else {
                    return null;
                }
            } catch (Exception x) {
                LOGGER.log(Level.FINE, null, x);
                return null;
            }
        }

        @Override public void checkAbortPermission() {
            AccessControlled ac = accessControlled();
            if (ac != null) {
                ac.checkPermission(AbstractProject.ABORT); // TODO https://trello.com/c/78J5G2zx/37-abstractproject-abort why is this defined in AbstractProject?
            }
        }

        @Override public boolean hasAbortPermission() {
            AccessControlled ac = accessControlled();
            return ac != null && ac.hasPermission(AbstractProject.ABORT);
        }

        private @CheckForNull Run<?,?> run() {
            try {
                if (!context.isReady()) {
                    return null;
                }
                return context.get(Run.class);
            } catch (Exception x) {
                LOGGER.log(Level.FINE, "broken " + cookie, x);
                finish(cookie); // probably broken, so just shut it down
                return null;
            }
        }

        @Override public String getUrl() {
            // TODO ideally this would be found via FlowExecution.owner.executable, but how do we check for something with a URL? There is no marker interface for it: https://trello.com/c/g6MDbyHJ/38-marker-interface-for-things-with-url
            Run<?,?> r = run();
            return r != null ? r.getUrl() : "";
        }

        @Override public String getDisplayName() {
            // TODO more generic to check whether FlowExecution.owner.executable is a ModelObject
            Run<?,?> r = run();
            return r != null ? "part of " + r.getFullDisplayName() : "part of unknown step";
        }

        @Override public String getName() {
            return getDisplayName();
        }

        @Override public String getFullDisplayName() {
            return getDisplayName();
        }

        @Override public long getEstimatedDuration() {
            return -1;
        }

        @Override public ResourceList getResourceList() {
            return new ResourceList();
        }

        @Override public Authentication getDefaultAuthentication() {
            return super.getDefaultAuthentication(); // TODO should pick up credentials from configuring user or something
        }

        @Override public boolean isPrioritized() {
            return cookie != null; // in which case this is after a restart and we still claim the executor
        }

        private static @CheckForNull StepContext finish(@CheckForNull String cookie) {
            if (cookie == null) {
                return null;
            }
            synchronized (runningTasks) {
                StepContext context = runningTasks.remove(cookie);
                if (context == null) {
                    LOGGER.log(Level.FINE, "no running task corresponds to {0}", cookie);
                }
                runningTasks.notifyAll();
                return context;
            }
        }

        private static final class Callback implements FutureCallback<Object>, Serializable {

            private final String cookie;
            private WorkspaceList.Lease lease;

            Callback(String cookie, WorkspaceList.Lease lease) {
                this.cookie = cookie;
                this.lease = lease;
            }

            @Override public void onSuccess(Object returnValue) {
                LOGGER.log(Level.FINE, "onSuccess {0}", cookie);
                lease.release();
                lease = null;
                StepContext context = finish(cookie);
                if (context != null) {
                    context.onSuccess(returnValue);
                }
            }

            @Override public void onFailure(Throwable t) {
                LOGGER.log(Level.FINE, "onFailure {0}", cookie);
                lease.release();
                lease = null;
                StepContext context = finish(cookie);
                if (context != null) {
                    context.onFailure(t);
                }
            }

        }

        private final class PlaceholderExecutable implements Queue.Executable {

            private static final String COOKIE_VAR = "JENKINS_SERVER_COOKIE";

            @Override public void run() {
                try {
                    Executor exec = Executor.currentExecutor();
                    if (exec == null) {
                        throw new IllegalStateException("running task without associated executor thread");
                    }
                    Computer computer = exec.getOwner();
                    // Set up context for other steps inside this one.
                    Node node = computer.getNode();
                    if (node == null) {
                        throw new IllegalStateException("running computer lacks a node");
                    }
                    TaskListener listener = context.get(TaskListener.class);
                    Launcher launcher = node.createLauncher(listener);
                    if (cookie == null) {
                        // First time around.
                        cookie = UUID.randomUUID().toString();
                        // Switches the label to a self-label, so if the executable is killed and restarted via ExecutorPickle, it will run on the same node:
                        label = computer.getName();
                        EnvVars env = new EnvVars(context.get(EnvVars.class));
                        env.put(COOKIE_VAR, cookie);
                        synchronized (runningTasks) {
                            runningTasks.put(cookie, context);
                        }
                        // For convenience, automatically a workspace, like WorkspaceStep would:
                        Run<?,?> r = context.get(Run.class);
                        Job<?,?> j = r.getParent();
                        if (!(j instanceof TopLevelItem)) {
                            throw new Exception(j + " must be a top-level job");
                        }
                        FilePath p = node.getWorkspaceFor((TopLevelItem) j);
                        WorkspaceList.Lease lease = computer.getWorkspaceList().allocate(p);
                        FilePath workspace = lease.path;
                        listener.getLogger().println("Running on " + computer.getDisplayName() + " in " + workspace); // TODO hyperlink
                        context.invokeBodyLater(new Callback(cookie, lease), exec, computer, computer.getChannel(), env, workspace);
                        LOGGER.log(Level.FINE, "started {0}", cookie);
                    } else {
                        // just rescheduled after a restart; wait for task to complete
                        LOGGER.log(Level.FINE, "resuming {0}", cookie);
                    }
                    try {
                        synchronized (runningTasks) {
                            while (runningTasks.containsKey(cookie)) {
                                LOGGER.log(Level.FINE, "waiting on {0}", cookie);
                                try {
                                    runningTasks.wait();
                                } catch (InterruptedException x) {
                                    // fine, Jenkins is shutting down
                                }
                            }
                        }
                    } finally {
                        try {
                            launcher.kill(Collections.singletonMap(COOKIE_VAR, cookie));
                        } catch (ChannelClosedException x) {
                            // fine, Jenkins was shutting down
                        } catch (RequestAbortedException x) {
                            // slave was exiting; too late to kill subprocesses
                        }
                    }
                } catch (Exception x) {
                    context.onFailure(x);
                }
            }

            @Override public SubTask getParent() {
                return PlaceholderTask.this;
            }
            
            @Override public long getEstimatedDuration() {
                return -1;
            }

            // TODO extract this from Run to a utility method in Executables: https://trello.com/c/6FVhT94X/39-executables-getexecutor
            @Restricted(DoNotUse.class) // for Jelly
            public @CheckForNull Executor getExecutor() {
                Jenkins j = Jenkins.getInstance();
                if (j == null) {
                    return null;
                }
                for (Computer c : j.getComputers()) {
                    for (Executor e : c.getExecutors()) {
                        if (e.getCurrentExecutable() == this) {
                            return e;
                        }
                    }
                }
                return null;
            }

            @Restricted(DoNotUse.class) // for Jelly
            public String getUrl() {
                return PlaceholderTask.this.getUrl(); // we hope this has a console.jelly
            }

            private static final long serialVersionUID = 1L;
        }

    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public Set<Class<?>> getRequiredContext() {
            Set<Class<?>> r = new HashSet<Class<?>>();
            // FlowExecution useful but currently not required
            r.add(TaskListener.class);
            r.add(EnvVars.class);
            r.add(Run.class);
            return r;
        }

        @Override public String getFunctionName() {
            return "node";
        }

        @Override public Step newInstance(Map<String,Object> arguments) {
            return new ExecutorStep((String) arguments.get("value"));
        }

        @Override public String getDisplayName() {
            return "Allocate node";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        // TODO completion on label field (cf. AbstractProjectDescriptor)

    }

}
