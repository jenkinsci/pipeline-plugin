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

package org.jenkinsci.plugins.workflow.job;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.test.RestartableJenkinsRule;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.JenkinsRule;

public class SegmentTest {

    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test public void basics() throws Exception {
        // Timeline (A has concurrency 2, B 1):
        // #1 o-A--------------B----------------o
        // #2        o-A-------------B     x
        // #3             o-A  ------------B    -------o
        //                     ^     ^     ^    ^      ^
        //                    B/1   B/2   B/3  X/1    X/2
        // (above are the semaphores we must signal)
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                        "steps.segment(value: 'A', concurrency: 2);\n" +
                        "steps.echo('in A');\n" +
                        "steps.semaphore('B');\n" +
                        "steps.segment(value: 'B', concurrency: 1);\n" +
                        "steps.echo('in B');\n" +
                        "steps.semaphore('X');\n" +
                        "steps.echo('done')"));
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
                    assertTrue(b3.isBuilding());
                    story.j.assertLogNotContains("done", b1);
                    story.j.assertLogNotContains("in B", b2);
                    story.j.assertLogNotContains("in B", b3);
                    SemaphoreStep.success("X/1", null);
                    e1.waitForSuspension();
                    assertFalse(b1.isBuilding());
                    e3.waitForSuspension();
                    assertTrue(b3.isBuilding());
                    story.j.assertLogContains("done", b1);
                    story.j.assertLogContains("in B", b3);
                    story.j.assertLogNotContains("done", b3);
                    SemaphoreStep.success("X/2", null);
                    e3.waitForSuspension();
                    assertFalse(b3.isBuilding());
                    story.j.assertLogContains("done", b3);
                } finally {
                    System.out.println(JenkinsRule.getLog(b1));
                    System.out.println(JenkinsRule.getLog(b2));
                    System.out.println(JenkinsRule.getLog(b3));
                }
            }
        });
    }

}
