package org.jenkinsci.plugins.workflow.steps;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.MilestoneStepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import static org.junit.Assert.*;

import hudson.model.Result;

public class MilestoneStepTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Before
    public void clear() {
        MilestoneStepExecution.clear();
    }

    @Test
    public void buildsMustPassThroughInOrder() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "semaphore 'inorder'\n" +
                        "echo 'Before milestone'\n" +
                        "milestone()\n" +
                        "echo 'Passed first milestone'\n" +
                        "milestone()\n" +
                        "echo 'Passed second milestone'\n" +
                        "milestone()\n" +
                        "echo 'Passed third milestone'\n"));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("inorder/1", b1);
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("inorder/2", b2);

                // Let #2 continue so it finish before #1
                SemaphoreStep.success("inorder/2", null);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b2));

                // Let #1 continue, so it must be early cancelled since #2 already went into milestone 1
                SemaphoreStep.success("inorder/1", null);
                story.j.assertBuildStatus(Result.NOT_BUILT, story.j.waitForCompletion(b1));
                story.j.assertLogNotContains("Passed first milestone", b1);
            }
        });
    }

    @Test
    public void olderBuildsMustBeCancelledOnMilestoneExit() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "milestone()\n" +
                        "echo 'First milestone'\n" +
                        "semaphore 'wait'\n" +
                        "milestone()\n" +
                        "echo 'Second milestone'\n"));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b1);
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/2", b2);
                // Now both #1 and #2 are into milestone 1

                // Let #2 continue so it leaves milestone 1
                SemaphoreStep.success("wait/2", null);
                story.j.waitForCompletion(b2);

                // Once #2 continues and reaches milestone 2 then #1 is automatically cancelled
                // TODO: determine why it's getting ABORTED instead of NOT_BUILT
                story.j.assertBuildStatus(Result.ABORTED, story.j.waitForCompletion(b1));
            }
        });
    }

    @Test
    public void olderBuildsMustBeCancelledOnBuildFinish() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "milestone label: 'My Label'\n" +
                        "node {\n" +
                        "  echo 'First milestone'\n" +
                        "  semaphore 'wait'\n" +
                        "}"));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b1);
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/2", b2);
                // Now both #1 and #2 are into milestone 1

                // Let #2 continue so it leaves milestone 1
                SemaphoreStep.success("wait/2", null);
                story.j.waitForCompletion(b2);

                // Once #2 continues and leaves milestone 1 then #1 is automatically cancelled
                // TODO: determine why it's getting ABORTED instead of NOT_BUILT
                story.j.assertBuildStatus(Result.ABORTED, story.j.waitForCompletion(b1));
            }
        });
    }

    @Test
    public void milestoneNotAllowedInsideParallel() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "sleep 1\n" +
                        "parallel one: { echo 'First' }, two: { \n" +
                        "  node {\n" +
                        "    echo 'Test'\n" +
                        "  }\n" +
                        "  milestone()\n" +
                        "}\n" +
                        "milestone()\n"));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                story.j.assertBuildStatus(Result.FAILURE, story.j.waitForCompletion(b1));
                story.j.assertLogContains("Milestone step found inside pararallel", b1);

                p.setDefinition(new CpsFlowDefinition(
                        "sleep 1\n" +
                        "parallel one: { echo 'First' }, two: { \n" +
                        "  echo 'Pre-node'\n" +
                        "  node {\n" +
                        "    milestone()\n" +
                        "  }\n" +
                        "}\n" +
                        "milestone()\n"));
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                story.j.assertBuildStatus(Result.FAILURE, story.j.waitForCompletion(b2));
                story.j.assertLogContains("Milestone step found inside pararallel", b2);

                p.setDefinition(new CpsFlowDefinition(
                        "sleep 1\n" +
                        "parallel one: { echo 'First' }, two: { \n" +
                        "  echo 'Pre-node'\n" +
                        "  node {\n" +
                        "    echo 'Inside node'\n" +
                        "  }\n" +
                        "}\n" +
                        "milestone()\n"));
                WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b3));
            }
        });
    }

}
