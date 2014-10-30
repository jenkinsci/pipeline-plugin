package org.jenkinsci.plugins.workflow.support.steps;

import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceList;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.SubTask;
import hudson.remoting.ChannelClosedException;
import hudson.remoting.RequestAbortedException;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.slaves.NodeProperty;
import hudson.slaves.WorkspaceList;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.durabletask.executors.ContinuableExecutable;
import org.jenkinsci.plugins.durabletask.executors.ContinuedTask;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.support.actions.WorkspaceActionImpl;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExecutorStepExecution extends StepExecution {

    @Inject private transient ExecutorStep step;
    @StepContextParameter private transient TaskListener listener;
    // Here just for requiredContext; could perhaps be passed to the PlaceholderTask constructor:
    @StepContextParameter private transient Run<?,?> run;
    @StepContextParameter private transient FlowExecution flowExecution;
    @StepContextParameter private transient FlowNode flowNode;

    /**
     * General strategy of this step.
     *
     * 1. schedule {@link PlaceholderTask} into the {@link Queue} (what this method does)
     * 2. when {@link PlaceholderTask} starts running, invoke the closure
     * 3. when the closure is done, let {@link PlaceholderTask} complete
     */
    @Override
    public boolean start() throws Exception {
        final PlaceholderTask task = new PlaceholderTask(getContext(), step.getLabel());
        if (Queue.getInstance().schedule2(task, 0).getCreateItem() == null) {
            // There can be no duplicates. But could be refused if a QueueDecisionHandler rejects it for some odd reason.
            throw new IllegalStateException("failed to schedule task");
        }
        Timer.get().schedule(new Runnable() {
            @Override public void run() {
                Queue.Item item = Queue.getInstance().getItem(task);
                if (item != null) {
                    PrintStream logger;
                    try {
                        logger = listener.getLogger();
                    } catch (Exception x) { // IOException, InterruptedException
                        LOGGER.log(Level.WARNING, null, x);
                        return;
                    }
                    logger.println("Still waiting to schedule task");
                    String why = item.getWhy();
                    if (why != null) {
                        logger.println(why);
                    }
                }
            }
        }, 15, TimeUnit.SECONDS);
        return false;
    }

    @Override
    public void stop() {
        for (Queue.Item item : Queue.getInstance().getItems()) {
            // if we are still in the queue waiting to be scheduled, just retract that
            if (item.task instanceof PlaceholderTask && ((PlaceholderTask) item.task).context.equals(getContext())) {
                Queue.getInstance().cancel(item);
                break;
            }
        }
        Jenkins j = Jenkins.getInstance();
        if (j != null) {
            // if we are already running, kill the ongoing activities, which releases PlaceholderExecutable from its sleep loop
            // Similar to Run.getExecutor (and proposed Executables.getExecutor), but distinct since we do not have the Executable yet:
            COMPUTERS: for (Computer c : j.getComputers()) {
                for (Executor e : c.getExecutors()) {
                    Queue.Executable exec = e.getCurrentExecutable();
                    if (exec instanceof PlaceholderTask.PlaceholderExecutable && ((PlaceholderTask.PlaceholderExecutable) exec).getParent().context.equals(getContext())) {
                        PlaceholderTask.finish(((PlaceholderTask.PlaceholderExecutable) exec).getParent().cookie);
                        break COMPUTERS;
                    }
                }
            }
        }
        // Whether or not either of the above worked (and they would not if for example our item were canceled), make sure we die.
        getContext().onFailure(new InterruptedException());
        // TODO also would like to listen for our queue item being canceled directly (Queue.cancel(Item)) and finish automatically,
        // but ScheduleResult.getCreateItem().getFuture().getStartCondition() is not a ListenableFuture so we cannot wait for it to be cancelled without consuming a thread,
        // and Item.cancel(Queue) is private and cannot be overridden; the only workaround for now is to have a custom QueueListener
    }

    private static final class PlaceholderTask implements ContinuedTask, Serializable {

        // TODO can this be replaced with StepExecutionIterator?
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

        @Override public CauseOfBlockage getCauseOfBlockage() {
            return null;
        }

        @Override public boolean isConcurrentBuild() {
            return false;
        }

        @Override public Collection<? extends SubTask> getSubTasks() {
            return Collections.singleton(this);
        }

        @Override public Queue.Task getOwnerTask() {
            Run<?,?> r = run();
            if (r != null && r.getParent() instanceof Queue.Task) {
                return (Queue.Task) r.getParent();
            } else {
                return this;
            }
        }

        @Override public Object getSameNodeConstraint() {
            return null;
        }

        // TODO not sure we need bother with all this, since there is not yet any clear linkage from Executor.interrupt to FlowExecution.abort
        private @CheckForNull
        AccessControlled accessControlled() {
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
                ac.checkPermission(Item.CANCEL);
            }
        }

        @Override public boolean hasAbortPermission() {
            AccessControlled ac = accessControlled();
            return ac != null && ac.hasPermission(Item.CANCEL);
        }

        private @CheckForNull
        Run<?,?> run() {
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
            return ACL.SYSTEM; // TODO should pick up credentials from configuring user or something
        }

        @Override public boolean isContinued() {
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

        /**
         * Called when the body closure is complete.
         */
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

        /**
         * Occupies {@link Executor} while workflow uses this slave.
         */
        private final class PlaceholderExecutable implements ContinuableExecutable {

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
                    Run<?,?> r = context.get(Run.class);
                    if (cookie == null) {
                        // First time around.
                        cookie = UUID.randomUUID().toString();
                        // Switches the label to a self-label, so if the executable is killed and restarted via ExecutorPickle, it will run on the same node:
                        label = computer.getName();
                        EnvVars env = new EnvVars();
                        env.put(COOKIE_VAR, cookie);
                        for (NodeProperty<?> nodeProperty : node.getNodeProperties()) {
                            nodeProperty.buildEnvVars(env, listener);
                        }
                        synchronized (runningTasks) {
                            runningTasks.put(cookie, context);
                        }
                        // For convenience, automatically allocate a workspace, like WorkspaceStep would:
                        Job<?,?> j = r.getParent();
                        if (!(j instanceof TopLevelItem)) {
                            throw new Exception(j + " must be a top-level job");
                        }
                        FilePath p = node.getWorkspaceFor((TopLevelItem) j);
                        WorkspaceList.Lease lease = computer.getWorkspaceList().allocate(p);
                        FilePath workspace = lease.path;
                        FlowNode flowNode = context.get(FlowNode.class);
                        flowNode.addAction(new WorkspaceActionImpl(workspace, flowNode));
                        listener.getLogger().println("Running on " + computer.getDisplayName() + " in " + workspace); // TODO hyperlink
                        context.invokeBodyLater(new Callback(cookie, lease), exec, computer, env, workspace);
                        LOGGER.log(Level.FINE, "started {0}", cookie);
                    } else {
                        // just rescheduled after a restart; wait for task to complete
                        LOGGER.log(Level.FINE, "resuming {0}", cookie);
                    }
                    try {
                        // wait until the invokeBodyLater call above completes and notifies our Callback object
                        synchronized (runningTasks) {
                            while (runningTasks.containsKey(cookie)) {
                                LOGGER.log(Level.FINE, "waiting on {0}", cookie);
                                try {
                                    runningTasks.wait();
                                } catch (InterruptedException x) {
                                    // Jenkins is shutting down or this task was interrupted (Executor.doStop)
                                    // TODO if the latter, we would like an API to StepExecution.stop the tip of our body
                                    exec.recordCauseOfInterruption(r, listener);
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

            @Override public PlaceholderTask getParent() {
                return PlaceholderTask.this;
            }

            @Override public long getEstimatedDuration() {
                return -1;
            }

            @Override public boolean willContinue() {
                synchronized (runningTasks) {
                    return runningTasks.containsKey(cookie);
                }
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

            @Restricted(NoExternalUse.class) // for Jelly and toString
            public String getUrl() {
                return PlaceholderTask.this.getUrl(); // we hope this has a console.jelly
            }

            @Override public String toString() {
                return "PlaceholderExecutable:" + getUrl() + ":" + cookie;
            }

            private static final long serialVersionUID = 1L;
        }
    }

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(ExecutorStepExecution.class.getName());
}
