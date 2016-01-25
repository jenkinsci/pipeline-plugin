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

import hudson.model.Result;
import jenkins.model.CauseOfInterruption;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class CatchErrorStepTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void specialStatus() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "catchError {\n" +
                "  semaphore 'specialStatus'\n" +
                "}"));
        SemaphoreStep.failure("specialStatus/1", new FlowInterruptedException(Result.UNSTABLE, new CauseOfInterruption.UserInterruption("smrt")));
        WorkflowRun b = p.scheduleBuild2(0).get();
        r.assertLogContains("smrt", r.assertBuildStatus(Result.UNSTABLE, b));
        /* TODO fixing this is trickier since CpsFlowExecution.setResult does not implement a public method, and anyway CatchErrorStep in its current location could not refer to FlowExecution:
        List<FlowNode> heads = b.getExecution().getCurrentHeads();
        assertEquals(1, heads.size());
        assertEquals(Result.UNSTABLE, ((FlowEndNode) heads.get(0)).getResult());
        */
    }

}
