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

package org.jenkinsci.plugins.workflow.multibranch;

import com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.OrganizationFolder;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitSCMSource;
import jenkins.security.NotReallyRoleSensitiveCallable;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.scm.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.steps.scm.GitStep;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class ReplayActionTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void scriptFromSCM() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "node {checkout scm; echo \"loaded ${readFile 'file'}\"}");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "file");
        sampleRepo.git("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsScmFlowDefinition(new GitStep(sampleRepo.toString()).createSCM(), "Jenkinsfile"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("loaded initial content", b);
        // Changing contents of a file in the repo. Since this is a standalone project, scm currently “floats” to the branch head.
        sampleRepo.write("file", "subsequent content");
        sampleRepo.git("add", "file");
        sampleRepo.git("commit", "--message=next");
        // Replaying with a modified main script; checkout scm will get branch head.
        b = (WorkflowRun) b.getAction(ReplayAction.class).run("node {checkout scm; echo \"this time loaded ${readFile 'file'}\"}").get();
        assertEquals(2, b.number);
        r.assertLogContains("this time loaded subsequent content", b);
    }

    @Test public void multibranch() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "node {checkout scm; echo readFile('file')}");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=init");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "master");
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertNotNull(b1);
        assertEquals(1, b1.getNumber());
        r.assertLogContains("initial content", b1);
        sampleRepo.write("file", "subsequent content");
        sampleRepo.git("add", "file");
        sampleRepo.git("commit", "--message=next");
        // Replaying main script with some upcasing.
        ScriptApproval.get().approveSignature("method java.lang.String toUpperCase");
        WorkflowRun b2 = (WorkflowRun) b1.getAction(ReplayAction.class).run("node {checkout scm; echo readFile('file').toUpperCase()}").get();
        assertEquals(2, b2.number);
        // For a multibranch project, we expect checkout scm to retrieve the same repository revision as the (original) Jenkinsfile.
        r.assertLogContains("INITIAL CONTENT", b2);
    }

    @Test public void permissions() throws Exception {
        File clones = tmp.newFolder();
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=init");
        sampleRepo.git("clone", ".", new File(clones, "one").getAbsolutePath());
        // Set up a secured instance with an organization folder.
        // Developers have varying permissions set at the topmost (configurable) level.
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        ProjectMatrixAuthorizationStrategy pmas = new ProjectMatrixAuthorizationStrategy();
        pmas.add(Jenkins.ADMINISTER, "admin");
        pmas.add(Jenkins.READ, "dev1");
        pmas.add(Jenkins.READ, "dev2");
        pmas.add(Jenkins.READ, "dev3");
        r.jenkins.setAuthorizationStrategy(pmas);
        OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
        Map<Permission, Set<String>> perms = new HashMap<Permission, Set<String>>();
        perms.put(Item.CONFIGURE, Collections.singleton("dev1")); // implies REPLAY
        perms.put(ReplayAction.REPLAY, Collections.singleton("dev2"));
        perms.put(Item.BUILD, Collections.singleton("dev3")); // does not imply REPLAY
        top.addProperty(new AuthorizationMatrixProperty(perms));
        top.getNavigators().add(new GitDirectorySCMNavigator(clones.getAbsolutePath()));
        top.scheduleBuild2(0).getFuture().get();
        top.getComputation().writeWholeLogTo(System.out);
        assertEquals(1, top.getItems().size());
        MultiBranchProject<?,?> one = top.getItem("one");
        r.waitUntilNoActivity();
        WorkflowJob p = WorkflowMultiBranchProjectTest.findBranchProject((WorkflowMultiBranchProject) one, "master");
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        // Multibranch projects are always sandboxed, so any dev with REPLAY (or CONFIGURE) can replay.
        assertTrue(canReplay(b1, "admin"));
        // Note that while dev1 cannot actually configure the WorkflowJob (it is read-only; no one can),
        // the implication CONFIGURE → REPLAY is done at a lower level than this suppression of CONFIGURE.
        assertTrue(canReplay(b1, "dev1"));
        assertTrue(canReplay(b1, "dev2"));
        assertFalse(canReplay(b1, "dev3"));
        // For whole-script-approval standalone projects, you need RUN_SCRIPTS to replay.
        p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("", false));
        b1 = p.scheduleBuild2(0).get();
        assertTrue(canReplay(b1, "admin"));
        assertFalse("not sandboxed, so only safe for admins", canReplay(b1, "dev1"));
        assertFalse(canReplay(b1, "dev2"));
        assertFalse(canReplay(b1, "dev3"));
    }
    private static boolean canReplay(WorkflowRun b, String user) {
        final ReplayAction a = b.getAction(ReplayAction.class);
        return ACL.impersonate(User.get(user).impersonate(), new NotReallyRoleSensitiveCallable<Boolean,RuntimeException>() {
            @Override public Boolean call() throws RuntimeException {
                return a.isEnabled();
            }
        });
    }

}
