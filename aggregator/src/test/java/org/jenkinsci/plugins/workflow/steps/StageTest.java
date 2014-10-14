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

import hudson.model.Result;
import java.util.List;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.StageStepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class StageTest {

    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

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
                assertTrue(JenkinsRule.getLog(b1), b1.isBuilding());
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                CpsFlowExecution e2 = (CpsFlowExecution) b2.getExecutionPromise().get();
                e2.waitForSuspension();
                assertTrue(b2.isBuilding());
                WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
                CpsFlowExecution e3 = (CpsFlowExecution) b3.getExecutionPromise().get();
                e3.waitForSuspension();
                assertTrue(b3.isBuilding());
                try {
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
                    Thread.sleep(1000); // TODO why is this necessary?
                    assertFalse(b2.isBuilding());
                    assertEquals(Result.NOT_BUILT, b2.getResult());
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
                } finally {
                    System.out.println(JenkinsRule.getLog(b1));
                    System.out.println(JenkinsRule.getLog(b2));
                    System.out.println(JenkinsRule.getLog(b3));
                }
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                StageStepExecution.clear();
                WorkflowJob p = story.j.jenkins.getItemByFullName("demo", WorkflowJob.class);
                WorkflowRun b1 = p.getBuildByNumber(1);
                WorkflowRun b3 = p.getBuildByNumber(3);
                try {
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
                } finally {
                    System.out.println(JenkinsRule.getLog(b1));
                    System.out.println(JenkinsRule.getLog(b3));
                }
            }
        });
    }

}
