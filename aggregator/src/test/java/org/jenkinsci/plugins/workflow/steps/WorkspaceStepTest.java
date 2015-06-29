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

package org.jenkinsci.plugins.workflow.steps;

import hudson.slaves.DumbSlave;
import java.io.File;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class WorkspaceStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-26072")
    @Test public void customWorkspace() throws Exception {
        DumbSlave s = r.createSlave();
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node('" + s.getNodeName() + "') {ws('custom-location') {echo pwd()}}", true));
        r.assertLogContains(s.getRemoteFS() + File.separator + "custom-location", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    @Issue("JENKINS-26072")
    @Test public void customWorkspaceConcurrency() throws Exception {
        // Currently limited to WorkspaceList.allocate:
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        // Use master as it has 2 executors by default, whereas createSlave hardcodes 1, and I do not want to bother creating a slave by hand:
        p.setDefinition(new CpsFlowDefinition("node {ws('custom-location') {echo pwd(); semaphore 'customWorkspace'}}", true));
        WorkflowRun b2 = p.scheduleBuild2(0).getStartCondition().get();
        SemaphoreStep.waitForStart("customWorkspace/1", b2);
        WorkflowRun b3 = p.scheduleBuild2(0).getStartCondition().get();
        SemaphoreStep.waitForStart("customWorkspace/2", b3);
        SemaphoreStep.success("customWorkspace/1", null);
        SemaphoreStep.success("customWorkspace/2", null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b2));
        r.assertBuildStatusSuccess(r.waitForCompletion(b3));
        String location = new File(r.jenkins.getRootDir(), "custom-location").getAbsolutePath();
        r.assertLogContains(location, b2);
        r.assertLogNotContains("custom-location@", b2);
        r.assertLogContains(location + "@2", b3);
    }

}
