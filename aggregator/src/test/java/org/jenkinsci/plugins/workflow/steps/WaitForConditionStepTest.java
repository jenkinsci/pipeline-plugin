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

import com.google.common.base.Function;
import hudson.AbortException;
import hudson.Util;
import hudson.model.Result;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.plugins.workflow.SingleJobTestBase;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.model.Statement;

public class WaitForConditionStepTest extends SingleJobTestBase {

    @Test public void simple() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition("waitUntil {semaphore 'waitSimple'}; semaphore 'waitedSimple'"));
                startBuilding();
                SemaphoreStep.waitForStart("waitSimple/1", b);
                SemaphoreStep.success("waitSimple/1", false);
                SemaphoreStep.waitForStart("waitSimple/2", b);
                SemaphoreStep.success("waitSimple/2", false);
                SemaphoreStep.waitForStart("waitSimple/3", b);
                SemaphoreStep.success("waitSimple/3", true);
                SemaphoreStep.waitForStart("waitedSimple/1", b);
                SemaphoreStep.success("waitedSimple/1", null);
                waitForWorkflowToComplete();
                assertBuildCompletedSuccessfully();
                story.j.assertLogContains("Will try again after " + Util.getTimeSpanString(WaitForConditionStep.Execution.MIN_RECURRENCE_PERIOD), b);
            }
        });
    }

    @Test public void failure() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition("waitUntil {semaphore 'waitFailure'}"));
                startBuilding();
                SemaphoreStep.waitForStart("waitFailure/1", b);
                SemaphoreStep.success("waitFailure/1", false);
                SemaphoreStep.waitForStart("waitFailure/2", b);
                String message = "broken condition";
                SemaphoreStep.failure("waitFailure/2", new AbortException(message));
                waitForWorkflowToComplete();
                story.j.assertBuildStatus(Result.FAILURE, b);
                story.j.assertLogContains(message, b);
            }
        });
    }

    @Test public void catchErrors() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  waitUntil {\n" +
                    "    try {\n" +
                    "      readFile 'flag'\n" +
                    "      true\n" +
                    // Note that catching a specific type verifies JENKINS-26164:
                    "    } catch (FileNotFoundException x) {\n" +
                    "      // x.printStackTrace()\n" +
                    "      semaphore 'waitCatchErrors'\n" +
                    "      false\n" +
                    "    }\n" +
                    "  }\n" +
                    "}\n" +
                    "echo 'finished waiting'"));
                startBuilding();
                SemaphoreStep.waitForStart("waitCatchErrors/1", b);
                jenkins().getWorkspaceFor(p).child("flag").write("", null);
                SemaphoreStep.success("waitCatchErrors/1", null);
                waitForWorkflowToComplete();
                assertBuildCompletedSuccessfully();
                story.j.assertLogContains("finished waiting", b);
            }
        });
    }

    @Test public void restartDuringBody() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition("waitUntil {semaphore 'waitRestartDuringBody'}; echo 'finished waiting'"));
                startBuilding();
                SemaphoreStep.waitForStart("waitRestartDuringBody/1", b);
                SemaphoreStep.success("waitRestartDuringBody/1", false);
                SemaphoreStep.waitForStart("waitRestartDuringBody/2", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                assertThatWorkflowIsSuspended();
                SemaphoreStep.success("waitRestartDuringBody/2", false);
                SemaphoreStep.waitForStart("waitRestartDuringBody/3", b);
                SemaphoreStep.success("waitRestartDuringBody/3", true);
                waitForWorkflowToComplete();
                assertBuildCompletedSuccessfully();
                story.j.assertLogContains("finished waiting", b);
            }
        });
    }

    @Ignore("TODO JENKINS-26163 executions.isEmpty() because StepExecution.applyAll is called while body is active")
    @Test public void restartDuringDelay() {
        story.addStep(new Statement() {
            @SuppressWarnings("SleepWhileInLoop")
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition("waitUntil {semaphore 'waitRestartDuringDelay'}; echo 'finished waiting'"));
                startBuilding();
                SemaphoreStep.waitForStart("waitRestartDuringDelay/1", b);
                final List<WaitForConditionStep.Execution> executions = new ArrayList<WaitForConditionStep.Execution>();
                StepExecution.applyAll(WaitForConditionStep.Execution.class, new Function<WaitForConditionStep.Execution, Void>() {
                    @Override public Void apply(WaitForConditionStep.Execution execution) {
                        executions.add(execution);
                        return null;
                    }
                }).get();
                assertEquals(1, executions.size());
                SemaphoreStep.success("waitRestartDuringDelay/1", false);
                SemaphoreStep.waitForStart("waitRestartDuringDelay/2", b);
                final long LONG_TIME = Long.MAX_VALUE / /* > RECURRENCE_PERIOD_BACKOFF */ 10;
                executions.get(0).recurrencePeriod = LONG_TIME;
                SemaphoreStep.success("waitRestartDuringDelay/2", false);
                while (executions.get(0).recurrencePeriod == LONG_TIME) {
                    Thread.sleep(100);
                }
                story.j.assertLogContains("Will try again after " + Util.getTimeSpanString(LONG_TIME), b);
                // timer is now waiting for a long time
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                assertThatWorkflowIsSuspended();
                SemaphoreStep.waitForStart("waitRestartDuringDelay/3", b);
                SemaphoreStep.success("waitRestartDuringDelay/3", false);
                SemaphoreStep.waitForStart("waitRestartDuringDelay/4", b);
                SemaphoreStep.success("waitRestartDuringDelay/4", true);
                waitForWorkflowToComplete();
                assertBuildCompletedSuccessfully();
                story.j.assertLogContains("finished waiting", b);
            }
        });
    }

}
