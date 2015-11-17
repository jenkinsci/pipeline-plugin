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

package org.jenkinsci.plugins.workflow.steps.input;

import org.jenkinsci.plugins.workflow.SingleJobTestBase;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;

public class InputStepRestartTest extends SingleJobTestBase {

    @Issue("JENKINS-25889")
    @Test public void restart() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition("input 'paused'"));
                startBuilding();
                story.j.waitForMessage("paused", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                InputAction a = b.getAction(InputAction.class);
                assertEquals(1, a.getExecutions().size());
                story.j.submit(story.j.createWebClient().getPage(b, a.getUrlName()).getFormByName(a.getExecutions().get(0).getId()), "proceed");
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
            }
        });
    }

}
