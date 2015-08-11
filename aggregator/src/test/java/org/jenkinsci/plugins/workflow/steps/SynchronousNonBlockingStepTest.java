package org.jenkinsci.plugins.workflow.steps;

import static org.junit.Assert.assertTrue;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class SynchronousNonBlockingStepTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void basicNonBlockingStep() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
            "echo 'First message'\n" +
            "syncnonblocking 'wait'\n" +
            "echo 'Second message'\n" +
        "}"));
        WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();

        // Stop here until syncnonblocking has printed the message "Test Sync Message"
        SynchronousNonBlockingStep.waitForStart("wait/1", b);

        // At this point the execution is paused inside the synchronous non-blocking step
        // Check for FlowNode created
        FlowGraphWalker walker = new FlowGraphWalker(b.getExecution());
        boolean found = false;
        for (FlowNode n = walker.next(); n != null; n = walker.next()) {
            if (n.getDisplayName().equals("Sync non-blocking Test step")) {
                found = true;
                break;
            }
        }

        System.out.println("Checking flow node added...");
        assertTrue("FlowNode has to be added just when the step starts running", found);

        // Check for message the test message sent to context listener
        System.out.println("Checking build log message present...");
        j.assertLogContains("Test Sync Message", b);
        // The last step did not run yet
        j.assertLogNotContains("Second message", b);

        // No need to call success since it's called in the syncnonblocking thread
        // SynchronousNonBlockingStep.success("wait/1", null);
        System.out.println("Waiting until syncnonblocking (and the full flow) finishes");
        j.waitUntilNoActivity();
        System.out.println("Build finished. Continue.");
        // Check for the last message
        j.assertLogContains("Second message", b);
        j.assertBuildStatusSuccess(b);
    }
}
