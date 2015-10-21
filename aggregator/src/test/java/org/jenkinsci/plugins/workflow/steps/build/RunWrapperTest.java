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

package org.jenkinsci.plugins.workflow.steps.build;

import hudson.model.Result;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

@Issue("JENKINS-26834")
public class RunWrapperTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule r = new RestartableJenkinsRule();

    @Test public void historyAndPickling() {
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                ScriptApproval.get().approveSignature("staticMethod org.codehaus.groovy.runtime.ScriptBytecodeAdapter compareNotEqual java.lang.Object java.lang.Object"); // TODO JENKINS-27390
                WorkflowJob p = r.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "def b0 = currentBuild\n" +
                    "for (b = b0; b != null; b = b.previousBuild) {\n" +
                    "  semaphore 'basics'\n" +
                    "  echo \"number=${b.number} result=${b.result}\"\n" +
                    "}", true));
                SemaphoreStep.success("basics/1", null);
                WorkflowRun b1 = r.j.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
                r.j.assertLogContains("number=1 result=null", b1);
                WorkflowRun b2 = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.success("basics/2", null);
                SemaphoreStep.waitForStart("basics/3", b2);
                r.j.waitForMessage("number=2 result=null", b2);
                r.j.assertLogNotContains("number=1", b2);
            }
        });
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = r.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b2 = p.getBuildByNumber(2);
                SemaphoreStep.success("basics/3", b2);
                r.j.assertBuildStatusSuccess(r.j.waitForCompletion(b2));
                r.j.assertLogContains("number=1 result=SUCCESS", b2);
            }
        });
    }

    @Test public void updateSelf() {
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                ScriptApproval.get().approveSignature("staticMethod org.codehaus.groovy.runtime.ScriptBytecodeAdapter compareNotEqual java.lang.Object java.lang.Object"); // TODO JENKINS-27390
                WorkflowJob p = r.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "currentBuild.result = 'UNSTABLE'\n" +
                    "currentBuild.description = 'manipulated'\n" +
                    "currentBuild.displayName = 'special'\n" +
                    "def pb = currentBuild.previousBuild; if (pb != null) {pb.displayName = 'verboten'}", true));
                WorkflowRun b1 = r.j.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get());
                assertEquals("manipulated", b1.getDescription());
                assertEquals("special", b1.getDisplayName());
                WorkflowRun b2 = r.j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
                assertEquals(SecurityException.class, b2.getExecution().getCauseOfFailure().getClass());
                assertEquals("manipulated", b2.getDescription());
                assertEquals("special", b2.getDisplayName());
                assertEquals("special", b1.getDisplayName());
            }
        });
    }

}
