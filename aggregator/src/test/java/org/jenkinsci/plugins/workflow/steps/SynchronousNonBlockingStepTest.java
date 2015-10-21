package org.jenkinsci.plugins.workflow.steps;

import static org.junit.Assert.assertTrue;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.Run;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jenkins.model.Jenkins;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepNode;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.inject.Inject;

public class SynchronousNonBlockingStepTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Test
    public void basicNonBlockingStep() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
            "echo 'First message'\n" +
            "syncnonblocking 'wait'\n" +
            "echo 'Second message'\n" +
        "}"));
        WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();

        // Wait for syncnonblocking to be started
        System.out.println("Waiting to syncnonblocking to start...");
        SynchronousNonBlockingStep.waitForStart("wait", b);

        // At this point the execution is paused inside the synchronous non-blocking step
        // Check for FlowNode created
        FlowGraphWalker walker = new FlowGraphWalker(b.getExecution());
        boolean found = false;
        // TODO: use iterator when https://github.com/jenkinsci/workflow-plugin/pull/178 merged
        for (FlowNode n = walker.next(); n != null; n = walker.next()) {
            if (n instanceof StepNode && ((StepNode) n).getDescriptor() instanceof SynchronousNonBlockingStep.DescriptorImpl) {
                found = true;
                break;
            }
        }

        System.out.println("Checking flow node added...");
        assertTrue("FlowNode has to be added just when the step starts running", found);

        // Check for message the test message sent to context listener
        System.out.println("Checking build log message present...");
        j.waitForMessage("Test Sync Message", b);
        // The last step did not run yet
        j.assertLogContains("First message", b);
        j.assertLogNotContains("Second message", b);

        // Let syncnonblocking to continue
        SynchronousNonBlockingStep.notify("wait");

        System.out.println("Waiting until syncnonblocking (and the full flow) finishes");
        j.waitForCompletion(b);
        System.out.println("Build finished. Continue.");
        // Check for the last message
        j.assertLogContains("Second message", b);
        j.assertBuildStatusSuccess(b);
    }

    @Test
    public void interruptedTest() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
            "echo 'First message'\n" +
            "try { syncnonblocking 'wait' } catch(InterruptedException e) { echo 'Interrupted!' }\n" +
            "echo 'Second message'\n" +
        "}"));
        WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();

        // Wait for syncnonblocking to be started
        System.out.println("Waiting to syncnonblocking to start...");
        SynchronousNonBlockingStep.waitForStart("wait", b);

        // At this point syncnonblocking is waiting for an interruption

        // Let's force a call to stop. This will try to send an interruption to the run Thread
        b.getExecutor().interrupt();
        System.out.println("Looking for interruption received log message");
        j.waitForMessage("Interrupted!", b);
        j.waitForCompletion(b);
        j.assertBuildStatus(Result.ABORTED, b);
    }

    @Test
    public void parallelTest() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
            "echo 'First message'\n" +
            "parallel( a: { syncnonblocking 'wait0'; echo 'a branch'; }, b: { semaphore 'wait1'; echo 'b branch'; } )\n" +
            "echo 'Second message'\n" +
        "}"));
        WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();

        SynchronousNonBlockingStep.waitForStart("wait0", b);
        SemaphoreStep.success("wait1/1", null);

        // Wait for "b" branch to print its message
        j.waitForMessage("b branch", b);
        System.out.println("b branch finishes");

        // Check that "a" branch is effectively blocked
        j.assertLogNotContains("a branch", b);

        // Notify "a" branch
        System.out.println("Continue on wait0");
        SynchronousNonBlockingStep.notify("wait0");

        // Wait for "a" branch to finish
        j.waitForMessage("a branch", b);
        j.waitForCompletion(b);
    }

    public static final class SynchronousNonBlockingStep extends AbstractStepImpl implements Serializable {

        public static final class State {
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
            final Set<String> started = new HashSet<String>();
        }

        private String id;

        @DataBoundConstructor
        public SynchronousNonBlockingStep(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public static void waitForStart(String id, Run<?,?> b) throws IOException, InterruptedException {
            State s = State.get();
            synchronized (s) {
                while (!s.started.contains(id)) {
                    if (b != null && !b.isBuilding()) {
                        throw new AssertionError();
                    }
                    s.wait(1000);
                }
            }
        }

        public static final void notify(String id) {
            State s = State.get();
            synchronized (s) {
                if (s.started.remove(id)) {
                    s.notifyAll();
                }
            }
        }

        public static class StepExecutionImpl extends AbstractSynchronousNonBlockingStepExecution<Void> {

            @Inject(optional=true) 
            private transient SynchronousNonBlockingStep step;

            @StepContextParameter
            private transient TaskListener listener;

            @Override
            protected Void run() throws Exception {
                System.out.println("Starting syncnonblocking " + step.getId());
                // Send a test message to the listener
                listener.getLogger().println("Test Sync Message");

                State s = State.get();
                synchronized (s) {
                    s.started.add(step.getId());
                    s.notifyAll();
                }

                // Wait until somone (main test thread) notify us
                System.out.println("Sleeping inside the syncnonblocking thread (" + step.getId() + ")");
                synchronized (s) {
                    while (s.started.contains(step.getId())) {
                        s.wait(1000);
                    }
                }
                System.out.println("Continue syncnonblocking " + step.getId());

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
}
