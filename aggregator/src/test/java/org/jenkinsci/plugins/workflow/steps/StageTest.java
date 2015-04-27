/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package org.jenkinsci.plugins.workflow.steps;

import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import java.util.List;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;
import org.jenkinsci.plugins.workflow.BuildWatcher;
import org.jenkinsci.plugins.workflow.JenkinsRuleExt;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.StageStepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class StageTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Before public void clear() {
        StageStepExecution.clear();
    }

    @Test public void basics() throws Exception {
        // Timeline (A has concurrency 2, B 1):
        // #1 o-A--------------B-----------------o
        // #2        o-A-------------B     x
        // #3             o-A  ------------B     -------o
        //                     ^     ^     ^     ^      ^
        //                    B/1   B/2   B/3 ^ X/1    X/2
        //                                (restart)
        // (above are the semaphores we must signal)
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                        "stage(name: 'A', concurrency: 2);\n" +
                        "echo('in A');\n" +
                        "semaphore('B');\n" +
                        "stage(name: 'B', concurrency: 1);\n" +
                        "echo('in B');\n" +
                        "semaphore('X');\n" +
                        "echo('done')"));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                CpsFlowExecution e1 = (CpsFlowExecution) b1.getExecutionPromise().get();
                e1.waitForSuspension();
                assertTrue(b1.isBuilding());
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                CpsFlowExecution e2 = (CpsFlowExecution) b2.getExecutionPromise().get();
                e2.waitForSuspension();
                assertTrue(b2.isBuilding());
                WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
                CpsFlowExecution e3 = (CpsFlowExecution) b3.getExecutionPromise().get();
                e3.waitForSuspension();
                assertTrue(b3.isBuilding());
                    story.j.assertLogContains("in A", b1);
                    story.j.assertLogNotContains("in B", b1);
                    story.j.assertLogContains("in A", b2);
                    story.j.assertLogNotContains("in B", b2);
                    story.j.assertLogNotContains("in A", b3);
                    SemaphoreStep.success("B/1", null);
                    e1.waitForSuspension();
                    assertTrue(b1.isBuilding());
                    e2.waitForSuspension();
                    assertTrue(b2.isBuilding());
                    e3.waitForSuspension();
                    assertTrue(b3.isBuilding());
                    story.j.assertLogContains("in B", b1);
                    story.j.assertLogNotContains("done", b1);
                    story.j.assertLogNotContains("in B", b2);
                    story.j.assertLogContains("in A", b3);
                    story.j.assertLogNotContains("in B", b3);
                    SemaphoreStep.success("B/2", null);
                    e1.waitForSuspension();
                    assertTrue(b1.isBuilding());
                    e2.waitForSuspension();
                    assertTrue(b2.isBuilding());
                    e3.waitForSuspension();
                    assertTrue(b3.isBuilding());
                    story.j.assertLogNotContains("done", b1);
                    story.j.assertLogNotContains("in B", b2);
                    story.j.assertLogNotContains("in B", b3);
                    SemaphoreStep.success("B/3", null);
                    e1.waitForSuspension();
                    assertTrue(b1.isBuilding());
                    e2.waitForSuspension();
                    e3.waitForSuspension();
                    story.j.assertBuildStatus(Result.NOT_BUILT, JenkinsRuleExt.waitForCompletion(b2));
                    InterruptedBuildAction iba = b2.getAction(InterruptedBuildAction.class);
                    assertNotNull(iba);
                    List<CauseOfInterruption> causes = iba.getCauses();
                    assertEquals(1, causes.size());
                    assertEquals(StageStepExecution.CanceledCause.class, causes.get(0).getClass());
                    assertEquals(b3, ((StageStepExecution.CanceledCause) causes.get(0)).getNewerBuild());
                    assertTrue(b3.isBuilding());
                    story.j.assertLogNotContains("done", b1);
                    story.j.assertLogNotContains("in B", b2);
                    story.j.assertLogNotContains("in B", b3);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                StageStepExecution.clear();
                WorkflowJob p = story.j.jenkins.getItemByFullName("demo", WorkflowJob.class);
                WorkflowRun b1 = p.getBuildByNumber(1);
                WorkflowRun b3 = p.getBuildByNumber(3);
                    assertTrue(b1.isBuilding());
                    story.j.assertLogNotContains("done", b1);
                    CpsFlowExecution e1 = (CpsFlowExecution) b1.getExecutionPromise().get();
                    e1.waitForSuspension();
                    assertTrue(b3.isBuilding());
                    story.j.assertLogNotContains("in B", b3);
                    CpsFlowExecution e3 = (CpsFlowExecution) b3.getExecutionPromise().get();
                    e3.waitForSuspension();
                    SemaphoreStep.success("X/1", null);
                    e1.waitForSuspension();
                    assertFalse(b1.isBuilding());
                    assertEquals(Result.SUCCESS, b1.getResult());
                    e3.waitForSuspension();
                    assertTrue(b3.isBuilding());
                    story.j.assertLogContains("done", b1);
                    story.j.assertLogContains("in B", b3);
                    story.j.assertLogNotContains("done", b3);
                    SemaphoreStep.success("X/2", null);
                    e3.waitForSuspension();
                    assertFalse(b3.isBuilding());
                    assertEquals(Result.SUCCESS, b3.getResult());
                    story.j.assertLogContains("done", b3);
            }
        });
    }

    @SuppressWarnings("SleepWhileInLoop")
    @Test public void serializability() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "try {\n" +
                        "  stage name: 'S', concurrency: 1\n" +
                        "  echo 'in A'\n" +
                        "  semaphore 'serializability'\n" +
                        "} finally {\n" +
                        "  node {\n" +
                        "    echo 'in finally'\n" +
                        "  }\n" +
                        "}"));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("serializability/1", b1);
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                JenkinsRuleExt.waitForMessage("Waiting for builds [1]", b2);
                WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
                JenkinsRuleExt.waitForMessage("Waiting for builds [1]", b3);
                SemaphoreStep.success("serializability/1", null); // b1
                story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b1));
                SemaphoreStep.success("serializability/2", null); // b3
                story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b3));
                story.j.assertBuildStatus(Result.NOT_BUILT, b2);
                story.j.assertLogContains("Canceled since #3 got here", b2);
                story.j.assertLogContains("in finally", b2);
            }
        });
    }

    @Issue("JENKINS-27052")
    @Test public void holdingAfterUnblock() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "stage name: 'A', concurrency: 1\n" +
                        "semaphore 'holdingAfterUnblockA'\n" +
                        "stage name: 'B', concurrency: 1\n" +
                        "semaphore 'holdingAfterUnblockB'\n" +
                        ""));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("holdingAfterUnblockA/1", b1); // about to leave A
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                JenkinsRuleExt.waitForMessage("Waiting for builds [1]", b2);
                SemaphoreStep.success("holdingAfterUnblockA/1", null);
                SemaphoreStep.waitForStart("holdingAfterUnblockB/1", b1); // now in B
                SemaphoreStep.waitForStart("holdingAfterUnblockA/2", b2); // b2 unblocked, now in A
                WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
                JenkinsRuleExt.waitForMessage("Waiting for builds [2]", b3);
            }
        });
    }

    @Issue("JENKINS-27052")
    @Test public void holdingAfterExitUnblock() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "stage name: 'A', concurrency: 1\n" +
                        "semaphore 'holdingAfterExitUnblock'\n" +
                        ""));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("holdingAfterExitUnblock/1", b1); // about to leave
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                JenkinsRuleExt.waitForMessage("Waiting for builds [1]", b2);
                SemaphoreStep.success("holdingAfterExitUnblock/1", null);
                JenkinsRuleExt.waitForCompletion(b1);
                SemaphoreStep.waitForStart("holdingAfterExitUnblock/2", b2); // b2 unblocked
                WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
                JenkinsRuleExt.waitForMessage("Waiting for builds [2]", b3);
            }
        });
    }

    @Test
    public void stageNameFromParameter() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("FLAG", null)));
                p.setDefinition(new CpsFlowDefinition(
                        "stage name: \"stage_${FLAG}\", concurrency: 1\n"
                        + "semaphore 'X'\n"
                        + ""));
                WorkflowRun b1 = p.scheduleBuild2(0, new ParametersAction(
                        new StringParameterValue("FLAG", "one"))).waitForStart();
                SemaphoreStep.waitForStart("X/1", b1);
                WorkflowRun b2 = p.scheduleBuild2(0, new ParametersAction(
                        new StringParameterValue("FLAG", "one"))).waitForStart();
                JenkinsRuleExt.waitForMessage("Waiting for builds [1]", b2);
                WorkflowRun b3 = p.scheduleBuild2(0, new ParametersAction(
                        new StringParameterValue("FLAG", "one"))).waitForStart();
                JenkinsRuleExt.waitForMessage("Waiting for builds [1]", b3);
                story.j.assertBuildStatus(Result.NOT_BUILT, JenkinsRuleExt.waitForCompletion(b2));
                story.j.assertLogContains("Canceled since #3 got here", b2);
                WorkflowRun b4 = p.scheduleBuild2(0, new ParametersAction(
                        new StringParameterValue("FLAG", "two"))).waitForStart();
                SemaphoreStep.waitForStart("X/2", b4);
                SemaphoreStep.success("X/2", null); // b4
                SemaphoreStep.success("X/1", null); // b1
                SemaphoreStep.waitForStart("X/3", b3);
                SemaphoreStep.success("X/3", null); // b3
                story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b4));
                story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b1));
                story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b3));
            }
        });
    }

    @Test
    public void newerBuildFinishesBeforeOlder() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "semaphore 'X'\n"
                        + "stage name: \"A\", concurrency: 1\n"
                        + "semaphore 'Y'\n"
                        + ""));
                // b1 will enter the stage, b2 and b3 will be started.
                // b3 will reach the stage before b2 does so b2 is canceled.
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("X/1", b1);
                SemaphoreStep.success("X/1", null); // b1
                SemaphoreStep.waitForStart("Y/1", b1); // b1 in stage

                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("X/2", b2);

                WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("X/3", b3);
                SemaphoreStep.success("X/3", null); // b3
                JenkinsRuleExt.waitForMessage("Waiting for builds [1]", b3); // b3 in stage waiting for b1

                SemaphoreStep.success("X/2", null); // b2 enters stage and get canceled
                JenkinsRuleExt.waitForMessage("Canceled since #3 got here", b2);
                story.j.assertBuildStatus(Result.NOT_BUILT, JenkinsRuleExt.waitForCompletion(b2));

                SemaphoreStep.success("Y/1", null); // b1
                SemaphoreStep.waitForStart("Y/2", b3);
                SemaphoreStep.success("Y/2", null); // b3
                story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b1));
                story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b3));                
            }
        });
    }
}
