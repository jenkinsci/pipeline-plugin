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

package org.jenkinsci.plugins.workflow.cps.replay;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;
import hudson.FilePath;
import hudson.XmlFile;
import hudson.cli.CLICommandInvoker;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
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
import java.io.File;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.security.NotReallyRoleSensitiveCallable;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.containsString;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class ReplayActionTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void editSimpleDefinition() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("echo 'first script'", false));
        // Start off with a simple run of the first script.
        WorkflowRun b1 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("first script", b1);
        // Now will replay with a second script.
        WorkflowRun b2;
        { // First time around, verify that UI elements are present and functional.
            ReplayAction a = b1.getAction(ReplayAction.class);
            assertNotNull(a);
            assertTrue(a.isEnabled());
            HtmlPage page = r.createWebClient().getPage(b1, a.getUrlName());
            HtmlForm form = page.getFormByName("config");
            HtmlTextArea text = form.getTextAreaByName("_.mainScript");
            assertEquals("echo 'first script'", text.getText());
            text.setText("echo 'second script'");
            // TODO loaded scripts
            HtmlPage redirect = r.submit(form);
            assertEquals(p.getAbsoluteUrl(), redirect.getUrl().toString());
            r.waitUntilNoActivity();
            b2 = p.getBuildByNumber(2);
            assertNotNull(b2);
        } // Subsequently can use the faster whitebox method.
        r.assertLogContains("second script", r.assertBuildStatusSuccess(b2));
        ReplayCause cause = b2.getCause(ReplayCause.class);
        assertNotNull(cause);
        assertEquals(1, cause.getOriginalNumber());
        assertEquals(b1, cause.getOriginal());
        assertEquals(b2, cause.getRun());
        // Replay #2 as #3. Note that the diff is going to be from #1 → #3, not #2 → #3.
        WorkflowRun b3 = (WorkflowRun) b2.getAction(ReplayAction.class).run("echo 'third script'").get();
        r.assertLogContains("third script", r.assertBuildStatusSuccess(b3));
        String diff = b3.getAction(ReplayAction.class).getDiff();
        assertThat(diff, containsString("-echo 'first script'"));
        assertThat(diff, containsString("+echo 'third script'"));
        System.out.println(diff);
        // Make a persistent edit to the script and run, just to make sure there is no lingering effect.
        p.setDefinition(new CpsFlowDefinition("echo 'fourth script'", false));
        r.assertLogContains("fourth script", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    @Test public void parameterized() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("param", "")));
        p.setDefinition(new CpsFlowDefinition("echo \"run with ${param}\"", true));
        WorkflowRun b1 = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("param", "some value"))));
        r.assertLogContains("run with some value", b1);
        // When we replay a parameterized build, we expect the original parameter values to be set.
        WorkflowRun b2 = (WorkflowRun) b1.getAction(ReplayAction.class).run("echo \"run again with ${param}\"").get();
        r.assertLogContains("run again with some value", r.assertBuildStatusSuccess(b2));
    }

    @Initializer(after=InitMilestone.EXTENSIONS_AUGMENTED, before=InitMilestone.JOB_LOADED) // same time as Jenkins global config is loaded (e.g., AuthorizationStrategy)
    public static void assertPermissionId() {
        String thePermissionId = "hudson.model.Run.Replay";
        // An AuthorizationStrategy may be loading a permission by name during Jenkins startup.
        Permission thePermission = Permission.fromId(thePermissionId);
        // Make sure it finds this addition, even though the PermissionGroup is in core.
        assertEquals(ReplayAction.REPLAY, thePermission);
        assertEquals(thePermissionId, thePermission.getId());
    }

    @Test public void permissions() throws Exception {
        // assertPermissionId should have been run before we get here
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        GlobalMatrixAuthorizationStrategy gmas = new GlobalMatrixAuthorizationStrategy();
        // Set up an administrator, and three developer users with varying levels of access.
        gmas.add(Jenkins.ADMINISTER, "admin");
        gmas.add(Jenkins.READ, "dev1");
        gmas.add(Item.CONFIGURE, "dev1"); // implies REPLAY
        gmas.add(Jenkins.READ, "dev2");
        List<Permission> permissions = Run.PERMISSIONS.getPermissions();
        assertThat(permissions, Matchers.hasItem(ReplayAction.REPLAY));
        gmas.add(ReplayAction.REPLAY, "dev2");
        gmas.add(Jenkins.READ, "dev3");
        gmas.add(Item.BUILD, "dev3"); // does not imply REPLAY
        r.jenkins.setAuthorizationStrategy(gmas);
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("", /* whole-script approval */ false));
        WorkflowRun b1 = p.scheduleBuild2(0).get();
        // Jenkins admins can of course do as they please. But developers without RUN_SCRIPTS are out of luck.
        assertTrue(canReplay(b1, "admin"));
        assertFalse("not sandboxed, so only safe for admins", canReplay(b1, "dev1"));
        assertFalse(canReplay(b1, "dev2"));
        assertFalse(canReplay(b1, "dev3"));
        p.setDefinition(new CpsFlowDefinition("", /* sandbox */ true));
        WorkflowRun b2 = p.scheduleBuild2(0).get();
        assertTrue(canReplay(b2, "admin"));
        // Developers with REPLAY (or CONFIGURE) can run it.
        assertTrue(canReplay(b2, "dev1"));
        assertTrue(canReplay(b2, "dev2"));
        assertFalse(canReplay(b2, "dev3"));
    }
    private static boolean canReplay(WorkflowRun b, String user) {
        final ReplayAction a = b.getAction(ReplayAction.class);
        return ACL.impersonate(User.get(user).impersonate(), new NotReallyRoleSensitiveCallable<Boolean,RuntimeException>() {
            @Override public Boolean call() throws RuntimeException {
                return a.isEnabled();
            }
        });
    }

    @Test public void loadStep() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        // Shortcut to simulate checking out an external repo with an auxiliary script.
        FilePath f = r.jenkins.getWorkspaceFor(p).child("f.groovy");
        f.write("echo 'original loaded text'", null);
        p.setDefinition(new CpsFlowDefinition("node {load 'f.groovy'}", true));
        // Initial build loads external script and prints a message.
        WorkflowRun b1 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("original loaded text", b1);
        // Editing main script to print an initial message, and editing loaded script as well.
        WorkflowRun b2 = (WorkflowRun) b1.getAction(ReplayAction.class).run("echo 'trying edits'\nnode {load 'f.groovy'}", Collections.singletonMap("Script1", "echo 'new loaded text'")).get();
        r.assertBuildStatusSuccess(b2);
        r.assertLogContains("trying edits", b2);
        r.assertLogContains("new loaded text", b2);
        // Can take a look at the build.xml and see that we are not duplicating script content once edits are applied (not yet formally asserted).
        System.out.println(new XmlFile(new File(b2.getRootDir(), "build.xml")).asString());
        // Diff should reflect both sets of changes.
        String diff = b2.getAction(ReplayAction.class).getDiff();
        assertThat(diff, containsString("+echo 'trying edits'"));
        assertThat(diff, containsString("Script1"));
        assertThat(diff, containsString("-echo 'original loaded text'"));
        assertThat(diff, containsString("+echo 'new loaded text'"));
        System.out.println(diff);
        // TODO test multiple loads
        // TODO load after restart
        // TODO test replay of replay
    }

    @Test public void cli() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        // As in loadStep, will set up a main and auxiliary script.
        FilePath f = r.jenkins.getWorkspaceFor(p).child("f.groovy");
        f.write("'original text'", null);
        p.setDefinition(new CpsFlowDefinition("node {def t = load 'f.groovy'; echo \"got ${t}\"}", true));
        WorkflowRun b1 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("got original text", b1);
        // s/got/received/ on main script
        assertEquals(0, new CLICommandInvoker(r, "replay-pipeline").withStdin(IOUtils.toInputStream("node {def t = load 'f.groovy'; echo \"received ${t}\"}")).invokeWithArgs("p").returnCode());
        r.waitUntilNoActivity();
        WorkflowRun b2 = p.getLastBuild();
        assertEquals(2, b2.getNumber());
        r.assertLogContains("received original text", b2);
        // s/original/new/ on auxiliary script, and explicitly asking to replay #1 rather than the latest
        assertEquals(0, new CLICommandInvoker(r, "replay-pipeline").withStdin(IOUtils.toInputStream("'new text'")).invokeWithArgs("p", "-n", "1", "-s", "Script1").returnCode());
        r.waitUntilNoActivity();
        WorkflowRun b3 = p.getLastBuild();
        assertEquals(3, b3.getNumber());
        // Main script picked up from #1, not #2.
        r.assertLogContains("got new text", b3);
    }

}
