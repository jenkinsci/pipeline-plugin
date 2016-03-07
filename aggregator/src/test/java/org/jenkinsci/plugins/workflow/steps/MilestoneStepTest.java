package org.jenkinsci.plugins.workflow.steps;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import hudson.model.Result;

public class MilestoneStepTest {

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test
    public void buildsMustPassThroughInOrder() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "milestone 1\n" +
                        "node {\n" +
                        "  echo 'First milestone'\n" +
                        "  semaphore 'inorder'\n" +
                        "}\n" +
                        "milestone 2\n" +
                        "node {\n" +
                        "  echo 'Second milestone'\n" +
                        "}"));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("inorder/1", b1);
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("inorder/2", b1);

                // Let #2 continue so it finish before #1
                SemaphoreStep.success("inorder/2", null);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b2));

                // Let #1 continue, so it must be cancelled since #2 already passed the milestone
                SemaphoreStep.success("inorder/1", null);
                story.j.assertBuildStatus(Result.NOT_BUILT, story.j.waitForCompletion(b1));
            }
        });
    }

}
