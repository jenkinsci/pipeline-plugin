/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import hudson.Util;
import hudson.model.Result;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.mercurial.MercurialInstallation;
import hudson.plugins.mercurial.MercurialSCMSource;
import hudson.tools.ToolProperty;
import java.io.File;
import java.util.Collections;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.impl.subversion.SubversionSCMSource;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.scm.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.steps.scm.MercurialSampleRepoRule;
import org.jenkinsci.plugins.workflow.steps.scm.SubversionSampleRepoRule;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class SCMBinderTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleGitRepo = new GitSampleRepoRule();
    @Rule public SubversionSampleRepoRule sampleSvnRepo = new SubversionSampleRepoRule();
    @Rule public MercurialSampleRepoRule sampleHgRepo = new MercurialSampleRepoRule();

    @Test public void exactRevisionGit() throws Exception {
        sampleGitRepo.init();
        ScriptApproval sa = ScriptApproval.get();
        sa.approveSignature("staticField hudson.model.Items XSTREAM2");
        sa.approveSignature("method com.thoughtworks.xstream.XStream toXML java.lang.Object");
        sampleGitRepo.write("Jenkinsfile", "echo hudson.model.Items.XSTREAM2.toXML(scm); semaphore 'wait'; node {checkout scm; echo readFile('file')}");
        sampleGitRepo.write("file", "initial content");
        sampleGitRepo.git("add", "Jenkinsfile");
        sampleGitRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleGitRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "master");
        SemaphoreStep.waitForStart("wait/1", null);
        WorkflowRun b1 = p.getLastBuild();
        assertNotNull(b1);
        assertEquals(1, b1.getNumber());
        assertRevisionAction(b1);
        sampleGitRepo.write("Jenkinsfile", "node {checkout scm; echo readFile('file').toUpperCase()}");
        sa.approveSignature("method java.lang.String toUpperCase");
        sampleGitRepo.write("file", "subsequent content");
        sampleGitRepo.git("commit", "--all", "--message=tweaked");
        SemaphoreStep.success("wait/1", null);
        sampleGitRepo.notifyCommit(r);
        WorkflowRun b2 = p.getLastBuild();
        assertEquals(2, b2.getNumber());
        r.assertLogContains("initial content", r.waitForCompletion(b1));
        r.assertLogContains("SUBSEQUENT CONTENT", b2);
        assertRevisionAction(b2);
    }

    static void assertRevisionAction(WorkflowRun build) {
        BuildData data = build.getAction(BuildData.class);
        assertNotNull(data);
        SCMRevisionAction revisionAction = build.getAction(SCMRevisionAction.class);
        assertNotNull(revisionAction);
        SCMRevision revision = revisionAction.getRevision();
        assertEquals(AbstractGitSCMSource.SCMRevisionImpl.class, revision.getClass());
        assertEquals(data.lastBuild.marked.getSha1().getName(), ((AbstractGitSCMSource.SCMRevisionImpl) revision).getHash());
    }

    @Test public void exactRevisionSubversion() throws Exception {
        sampleSvnRepo.init();
        ScriptApproval sa = ScriptApproval.get();
        sa.approveSignature("staticField hudson.model.Items XSTREAM2");
        sa.approveSignature("method com.thoughtworks.xstream.XStream toXML java.lang.Object");
        sampleSvnRepo.write("Jenkinsfile", "echo hudson.model.Items.XSTREAM2.toXML(scm); semaphore 'wait'; node {checkout scm; echo readFile('file')}");
        sampleSvnRepo.write("file", "initial content");
        sampleSvnRepo.svn("add", "Jenkinsfile");
        sampleSvnRepo.svn("commit", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new SubversionSCMSource(null, sampleSvnRepo.prjUrl(), null, null, null), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "trunk");
        SemaphoreStep.waitForStart("wait/1", null);
        WorkflowRun b1 = p.getLastBuild();
        assertNotNull(b1);
        assertEquals(1, b1.getNumber());
        sampleSvnRepo.write("Jenkinsfile", "node {checkout scm; echo readFile('file').toUpperCase()}");
        sa.approveSignature("method java.lang.String toUpperCase");
        sampleSvnRepo.write("file", "subsequent content");
        sampleSvnRepo.svn("commit", "--message=tweaked");
        SemaphoreStep.success("wait/1", null);
        WorkflowRun b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(2, b2.getNumber());
        r.assertLogContains("initial content", r.waitForCompletion(b1));
        r.assertLogContains("SUBSEQUENT CONTENT", b2);
    }

    @Test public void exactRevisionMercurial() throws Exception {
        sampleHgRepo.init();
        ScriptApproval sa = ScriptApproval.get();
        sa.approveSignature("staticField hudson.model.Items XSTREAM2");
        sa.approveSignature("method com.thoughtworks.xstream.XStream toXML java.lang.Object");
        sampleHgRepo.write("Jenkinsfile", "echo hudson.model.Items.XSTREAM2.toXML(scm); semaphore 'wait'; node {checkout scm; echo readFile('file')}");
        sampleHgRepo.write("file", "initial content");
        sampleHgRepo.hg("commit", "--addremove", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        String instName = "caching";
        r.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(
                new MercurialInstallation(instName, "", "hg", false, true, false, null, Collections.<ToolProperty<?>> emptyList()));
        /* Does not actually seem to be necessary:
        { // TODO MercurialSCM.CACHE_LOCAL_REPOS = true;
            Field CACHE_LOCAL_REPOS = MercurialSCM.class.getDeclaredField("CACHE_LOCAL_REPOS");
            CACHE_LOCAL_REPOS.setAccessible(true);
            CACHE_LOCAL_REPOS.set(null, true);
        }
        */
        mp.getSourcesList().add(new BranchSource(new MercurialSCMSource(null, instName, sampleHgRepo.fileUrl(), null, null, null, null, null, true), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "default");
        SemaphoreStep.waitForStart("wait/1", null);
        WorkflowRun b1 = p.getLastBuild();
        assertNotNull(b1);
        assertEquals(1, b1.getNumber());
        sampleHgRepo.write("Jenkinsfile", "node {checkout scm; echo readFile('file').toUpperCase()}");
        sa.approveSignature("method java.lang.String toUpperCase");
        sampleHgRepo.write("file", "subsequent content");
        sampleHgRepo.hg("commit", "--message=tweaked");
        SemaphoreStep.success("wait/1", null);
        WorkflowRun b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(2, b2.getNumber());
        r.assertLogContains("initial content", r.waitForCompletion(b1));
        r.assertLogContains("SUBSEQUENT CONTENT", b2);
    }

    @Test public void deletedJenkinsfile() throws Exception {
        sampleGitRepo.init();
        sampleGitRepo.write("Jenkinsfile", "node { echo 'Hello World' }");
        sampleGitRepo.git("add", "Jenkinsfile");
        sampleGitRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleGitRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "master");
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        sampleGitRepo.git("rm", "Jenkinsfile");
        sampleGitRepo.git("commit", "--all", "--message=remove");
        WorkflowRun b2 = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        r.assertLogContains("Jenkinsfile not found", b2);
    }

    @Test public void deletedBranch() throws Exception {
        sampleGitRepo.init();
        // TODO GitSCMSource offers no way to set a GitSCMExtension such as CleanBeforeCheckout; work around with deleteDir
        // (without cleaning, b2 will succeed since the workspace will still have a cached origin/feature ref)
        sampleGitRepo.write("Jenkinsfile", "node {deleteDir(); checkout scm; echo 'Hello World'}");
        sampleGitRepo.git("add", "Jenkinsfile");
        sampleGitRepo.git("commit", "--all", "--message=flow");
        sampleGitRepo.git("checkout", "-b", "feature");
        sampleGitRepo.write("somefile", "stuff");
        sampleGitRepo.git("add", "somefile");
        sampleGitRepo.git("commit", "--all", "--message=tweaked");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleGitRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "feature");
        assertEquals(2, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        sampleGitRepo.git("checkout", "master");
        sampleGitRepo.git("branch", "-D", "feature");
        { // TODO AbstractGitSCMSource.retrieve is incorrect: after fetching remote refs into the cache, the origin/feature ref remains locally even though it has been deleted upstream:
            Util.deleteRecursive(new File(r.jenkins.getRootDir(), "caches"));
        }
        WorkflowRun b2 = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        r.assertLogContains("nondeterministic checkout", b2); // SCMBinder
        r.assertLogContains("any revision to build", b2); // checkout scm
        mp.scheduleBuild2(0).getFuture().get();
        assertEquals(1, mp.getItems().size());
    }

}
