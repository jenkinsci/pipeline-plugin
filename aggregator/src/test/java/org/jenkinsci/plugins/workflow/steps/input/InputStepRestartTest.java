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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.SingleJobTestBase;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStepExecution;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.recipes.LocalData;

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
                proceed(b);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                sanity(b);
            }
        });
    }
    
    private void proceed(WorkflowRun b) throws Exception {
        InputAction a = b.getAction(InputAction.class);
        assertNotNull(a);
        assertEquals(1, a.getExecutions().size());
        story.j.submit(story.j.createWebClient().getPage(b, a.getUrlName()).getFormByName(a.getExecutions().get(0).getId()), "proceed");
    }

    private void sanity(WorkflowRun b) throws Exception {
        List<PauseAction> pauses = new ArrayList<PauseAction>();
        for (FlowNode n : new FlowGraphWalker(b.getExecution())) {
            pauses.addAll(PauseAction.getPauseActions(n));
        }
        assertEquals(1, pauses.size());
        assertFalse(pauses.get(0).isPaused());
        String xml = FileUtils.readFileToString(new File(b.getRootDir(), "build.xml"));
        assertFalse(xml, xml.contains(InputStepExecution.class.getName()));
    }

    @Issue("JENKINS-25889")
    @LocalData // from 1.4.2
    @Test public void oldFlow() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = jenkins().getItemByFullName("p", WorkflowJob.class);
                assertNotNull(p);
                WorkflowRun b = p.getLastBuild();
                assertNotNull(b);
                assertEquals(1, b.getNumber());
                proceed(b);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                sanity(b);
            }
        });
    }

}
