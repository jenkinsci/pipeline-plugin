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

import hudson.model.Item;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.Permission;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.security.NotReallyRoleSensitiveCallable;
import org.hamcrest.Matchers;
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
        /* TODO broken by ACE integration, figure out how to resurrect:
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
        */
        b2 = (WorkflowRun) b1.getAction(RerunAction.class).run("echo 'second script'").get();
        r.assertLogContains("second script", r.assertBuildStatusSuccess(b2));
        RerunCause cause = b2.getCause(RerunCause.class);
        assertNotNull(cause);
        assertEquals(1, cause.getNumber());
        assertEquals(b1, cause.getOriginal());
        WorkflowRun b3 = (WorkflowRun) b2.getAction(RerunAction.class).run("echo 'third script'").get();
        r.assertLogContains("third script", r.assertBuildStatusSuccess(b3));
        p.setDefinition(new CpsFlowDefinition("echo 'fourth script'", false));
        r.assertLogContains("fourth script", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    @Test public void parameterized() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("param", "")));
        p.setDefinition(new CpsFlowDefinition("echo \"run with ${param}\"", true));
        WorkflowRun b1 = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("param", "some value"))));
        r.assertLogContains("run with some value", b1);
        WorkflowRun b2 = (WorkflowRun) b1.getAction(RerunAction.class).run("echo \"run again with ${param}\"").get();
        r.assertLogContains("run again with some value", r.assertBuildStatusSuccess(b2));
    }

    @Test public void permissions() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        GlobalMatrixAuthorizationStrategy gmas = new GlobalMatrixAuthorizationStrategy();
        gmas.add(Jenkins.ADMINISTER, "admin");
        gmas.add(Jenkins.READ, "dev1");
        gmas.add(Item.CONFIGURE, "dev1"); // implies RERUN
        gmas.add(Jenkins.READ, "dev2");
        List<Permission> permissions = Run.PERMISSIONS.getPermissions();
        assertThat(permissions, Matchers.hasItem(RerunAction.RERUN));
        gmas.add(RerunAction.RERUN, "dev2");
        gmas.add(Jenkins.READ, "dev3");
        gmas.add(Item.BUILD, "dev3"); // does not imply RERUN
        r.jenkins.setAuthorizationStrategy(gmas);
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("", false));
        WorkflowRun b1 = p.scheduleBuild2(0).get();
        assertTrue(canRerun(b1, "admin"));
        assertFalse("not sandboxed, so only safe for admins", canRerun(b1, "dev1"));
        assertFalse(canRerun(b1, "dev2"));
        assertFalse(canRerun(b1, "dev3"));
        p.setDefinition(new CpsFlowDefinition("", true));
        WorkflowRun b2 = p.scheduleBuild2(0).get();
        assertTrue(canRerun(b2, "admin"));
        assertTrue(canRerun(b2, "dev1"));
        assertTrue(canRerun(b2, "dev2"));
        assertFalse(canRerun(b2, "dev3"));
    }
    private static boolean canRerun(WorkflowRun b, String user) {
        final RerunAction a = b.getAction(RerunAction.class);
        return ACL.impersonate(User.get(user).impersonate(), new NotReallyRoleSensitiveCallable<Boolean,RuntimeException>() {
            @Override public Boolean call() throws RuntimeException {
                return a.isEnabled();
            }
        });
    }

}
