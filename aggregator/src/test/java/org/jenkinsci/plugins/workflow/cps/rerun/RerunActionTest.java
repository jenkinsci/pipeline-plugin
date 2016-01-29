/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.cps.rerun;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class RerunActionTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void editSimpleDefinition() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("echo 'first script'", false));
        WorkflowRun b1 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("first script", b1);
        WorkflowRun b2;
        { // First time around, verify that UI elements are present and functional.
            RerunAction a = b1.getAction(RerunAction.class);
            assertNotNull(a);
            assertTrue(a.isEnabled());
            HtmlPage page = r.createWebClient().getPage(b1, a.getUrlName());
            HtmlForm form = page.getFormByName("config");
            HtmlTextArea text = form.getTextAreaByName("_.script");
            assertEquals("echo 'first script'", text.getText());
            text.setText("echo 'second script'");
            HtmlPage redirect = r.submit(form);
            assertEquals(p.getAbsoluteUrl(), redirect.getUrl().toString());
            r.waitUntilNoActivity();
            b2 = p.getBuildByNumber(2);
            assertNotNull(b2);
        } // Subsequently can use the faster whitebox method.
        r.assertLogContains("second script", r.assertBuildStatusSuccess(b2));
        RerunCause cause = b2.getCause(RerunCause.class);
        assertNotNull(cause);
        assertEquals(1, cause.getNumber());
        WorkflowRun b3 = (WorkflowRun) b2.getAction(RerunAction.class).run("echo 'third script'").get();
        r.assertLogContains("third script", r.assertBuildStatusSuccess(b3));
        p.setDefinition(new CpsFlowDefinition("echo 'fourth script'", false));
        r.assertLogContains("fourth script", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    // TODO test sandbox usage
    // TODO test permissions
    // TODO test CpsScmFlowDefinition
    // TODO test WorkflowMultiBranchProject

}
