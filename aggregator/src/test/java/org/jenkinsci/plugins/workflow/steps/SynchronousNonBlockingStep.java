package org.jenkinsci.plugins.workflow.steps;

import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import jenkins.model.Jenkins;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.inject.Inject;

public final class SynchronousNonBlockingStep extends AbstractStepImpl implements Serializable {

    /** State of semaphore steps within one Jenkins home and thus (possibly restarting) test. */
    private static final class State {
        private static final Map<File,State> states = new HashMap<File,State>();
        static synchronized State get() {
            File home = Jenkins.getActiveInstance().getRootDir();
            State state = states.get(home);
            if (state == null) {
                state = new State();
                states.put(home, state);
            }
            return state;
        }
        private State() {}
        private final Map<String,Integer> iota = new HashMap<String,Integer>();
        synchronized int allocateNumber(String id) {
            Integer old = iota.get(id);
            if (old == null) {
                old = 0;
            }
            int number = old + 1;
            iota.put(id, number);
            return number;
        }
        /** map from {@link #k} to serial form of {@link StepContext} */
        final Map<String,String> contexts = new HashMap<String,String>();
        final Map<String,Object> returnValues = new HashMap<String,Object>();
        final Map<String,Throwable> errors = new HashMap<String,Throwable>();
        final Set<String> started = new HashSet<String>();
    }

    private final String id;
    private final int number;

    @DataBoundConstructor 
    public SynchronousNonBlockingStep(String id) {
        this.id = id;
        number = State.get().allocateNumber(id);
    }

    public String getId() {
        return id;
    }

    private String k() {
        return id + "/" + number;
    }

    public StepContext getContext() {
        return getContext(State.get(), k());
    }

    private static StepContext getContext(State s, String k) {
        return (StepContext) Jenkins.XSTREAM.fromXML(s.contexts.get(k));
    }

    public static void waitForStart(@Nonnull String k, @CheckForNull Run<?,?> b) throws IOException, InterruptedException {
        State s = State.get();
        synchronized (s) {
            while (!s.started.contains(k)) {
                if (b != null && !b.isBuilding()) {
                    throw new AssertionError(JenkinsRule.getLog(b));
                }
                s.wait(1000);
            }
        }
    }

    public static class StepExecutionImpl extends AbstractSynchronousNonBlockingStepExecution<Void> {

        @Inject(optional=true) 
        private SynchronousNonBlockingStep step;

        @Override
        protected Void run() throws Exception {
            // Send a test message to the listener
            getContext().get(TaskListener.class).getLogger().println("Test Sync Message");

            State s = State.get();
            String k = step.k();
            if (s.returnValues.containsKey(k)) {
                System.err.println("Immediately running " + k);
                getContext().onSuccess(s.returnValues.get(k));
            } else if (s.errors.containsKey(k)) {
                System.err.println("Immediately failing " + k);
                getContext().onFailure(s.errors.get(k));
            } else {
                System.err.println("Blocking " + k);
                s.contexts.put(k, Jenkins.XSTREAM.toXML(getContext()));
            }

            // Let's wait 2 seconds to give time to the main test thread to reach the wait point
            Thread.sleep(2000);

            synchronized (s) {
                s.started.add(k);
                s.notifyAll();
            }

            // Let's wait 2 additional seconds after unblocking the test thread
            // During this 2 seconds it will check for the messages in the build log
            System.out.println("Sleeping inside the syncnonblocking thread");
            Thread.sleep(2000);
            System.out.println("Continue syncnonblocking");

            return null;
        }

        private static final long serialVersionUID = 1L;
    }

    @TestExtension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(StepExecutionImpl.class);
        }

        @Override
        public String getFunctionName() {
            return "syncnonblocking";
        }

        @Override
        public String getDisplayName() {
            return "Sync non-blocking Test step";
        }

    }

    private static final long serialVersionUID = 1L;

}
