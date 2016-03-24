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
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class WaitForConditionStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test public void simple() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("waitUntil {semaphore 'wait'}; semaphore 'waited'"));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
                SemaphoreStep.success("wait/1", false);
                SemaphoreStep.waitForStart("wait/2", b);
                SemaphoreStep.success("wait/2", false);
                SemaphoreStep.waitForStart("wait/3", b);
                SemaphoreStep.success("wait/3", true);
                SemaphoreStep.waitForStart("waited/1", b);
                SemaphoreStep.success("waited/1", null);
                story.j.assertLogContains("Will try again after " + Util.getTimeSpanString(WaitForConditionStep.Execution.MIN_RECURRENCE_PERIOD), story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b)));
            }
        });
    }

    @Test public void failure() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("waitUntil {semaphore 'wait'}"));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
                SemaphoreStep.success("wait/1", false);
                SemaphoreStep.waitForStart("wait/2", b);
                String message = "broken condition";
                SemaphoreStep.failure("wait/2", new AbortException(message));
                // TODO the following fails (missing message) when run as part of whole suite, but not standalone: story.j.assertLogContains(message, story.j.assertBuildStatus(Result.FAILURE, story.j.waitForCompletion(b)));
                story.j.waitForCompletion(b);
                story.j.assertBuildStatus(Result.FAILURE, b);
                story.j.assertLogContains(message, b);
            }
        });
    }

    @Test public void catchErrors() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  waitUntil {\n" +
                    "    try {\n" +
                    "      readFile 'flag'\n" +
                    "      true\n" +
                    // Note that catching a specific type verifies JENKINS-26164:
                    "    } catch (FileNotFoundException x) {\n" +
                    "      // x.printStackTrace()\n" +
                    "      semaphore 'wait'\n" +
                    "      false\n" +
                    "    }\n" +
                    "  }\n" +
                    "}\n" +
                    "echo 'finished waiting'"));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
                story.j.jenkins.getWorkspaceFor(p).child("flag").write("", null);
                SemaphoreStep.success("wait/1", null);
                story.j.assertLogContains("finished waiting", story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b)));
            }
        });
    }

    @Test public void restartDuringBody() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("waitUntil {semaphore 'wait'}; echo 'finished waiting'"));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
                SemaphoreStep.success("wait/1", false);
                SemaphoreStep.waitForStart("wait/2", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowRun b = story.j.jenkins.getItemByFullName("p", WorkflowJob.class).getBuildByNumber(1);
                SemaphoreStep.success("wait/2", false);
                SemaphoreStep.waitForStart("wait/3", b);
                SemaphoreStep.success("wait/3", true);
                story.j.assertLogContains("finished waiting", story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b)));
            }
        });
    }

    @Test public void restartDuringDelay() {
        story.addStep(new Statement() {
            @SuppressWarnings("SleepWhileInLoop")
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("waitUntil {semaphore 'wait'}; echo 'finished waiting'"));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
                final List<WaitForConditionStep.Execution> executions = new ArrayList<WaitForConditionStep.Execution>();
                StepExecution.applyAll(WaitForConditionStep.Execution.class, new Function<WaitForConditionStep.Execution, Void>() {
                    @Override public Void apply(WaitForConditionStep.Execution execution) {
                        executions.add(execution);
                        return null;
                    }
                }).get();
                assertEquals(1, executions.size());
                SemaphoreStep.success("wait/1", false);
                SemaphoreStep.waitForStart("wait/2", b);
                final long LONG_TIME = Long.MAX_VALUE / /* > RECURRENCE_PERIOD_BACKOFF */ 10;
                executions.get(0).recurrencePeriod = LONG_TIME;
                SemaphoreStep.success("wait/2", false);
                while (executions.get(0).recurrencePeriod == LONG_TIME) {
                    Thread.sleep(100);
                }
                story.j.waitForMessage("Will try again after " + Util.getTimeSpanString(LONG_TIME), b);
                // timer is now waiting for a long time
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowRun b = story.j.jenkins.getItemByFullName("p", WorkflowJob.class).getBuildByNumber(1);
                SemaphoreStep.waitForStart("wait/3", b);
                SemaphoreStep.success("wait/3", false);
                SemaphoreStep.waitForStart("wait/4", b);
                SemaphoreStep.success("wait/4", true);
                story.j.assertLogContains("finished waiting", story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b)));
            }
        });
    }

}
