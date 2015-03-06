/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

package org.jenkinsci.plugins.workflow;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

@Issue("JENKINS-26834")
public class BuildVarTest {

    @Rule public RestartableJenkinsRule r = new RestartableJenkinsRule();

    @Test public void historyAndPickling() {
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = r.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "for (b = build; b != null; b = b.previousBuild) {\n" +
                    "  semaphore 'basics'\n" +
                    // TODO JENKINS-27271 cannot simply use ${b.result?.isWorseThan(hudson.model.Result.SUCCESS)}
                    "  def r = b.result; echo \"number=${b.number} problem=${r != null ? r.isWorseThan(hudson.model.Result.SUCCESS) : null}\"\n" +
                    "}", true));
                SemaphoreStep.success("basics/1", null);
                WorkflowRun b1 = r.j.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
                r.j.assertLogContains("number=1 problem=null", b1);
                WorkflowRun b2 = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.success("basics/2", null);
                SemaphoreStep.waitForStart("basics/3", b2);
                Thread.sleep(1000); // TODO why is this necessary? will the flush() in #80 help?
                r.j.assertLogContains("number=2 problem=null", b2);
                r.j.assertLogNotContains("number=1", b2);
            }
        });
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = r.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b2 = p.getBuildByNumber(2);
                SemaphoreStep.success("basics/3", b2);
                while (b2.isBuilding()) {
                    Thread.sleep(100);
                }
                r.j.assertBuildStatusSuccess(b2);
                r.j.assertLogContains("number=1 problem=false", b2);
            }
        });
    }

}
