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

package org.jenkinsci.plugins.workflow.steps.scm;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Label;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitTagAction;
import hudson.plugins.git.util.BuildData;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.triggers.SCMTrigger;
import java.util.Iterator;
import java.util.List;
import jenkins.util.VirtualFile;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import static org.junit.Assert.*;
import org.junit.ClassRule;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class GitStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();
    @Rule public GitSampleRepoRule otherRepo = new GitSampleRepoRule();

    @Test
    public void roundtrip() throws Exception {
        GitStep step = new GitStep("git@github.com:jenkinsci/workflow-plugin.git");
        Step roundtrip = new StepConfigTester(r).configRoundTrip(step);
        r.assertEqualDataBoundBeans(step, roundtrip);
    }

    @Test
    public void roundtrip_withcredentials() throws Exception {
        IdCredentials c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, null, null, "user", "pass");
        CredentialsProvider.lookupStores(r.jenkins).iterator().next()
                .addCredentials(Domain.global(), c);
        GitStep step = new GitStep("git@github.com:jenkinsci/workflow-plugin.git");
        step.setCredentialsId(c.getId());
        Step roundtrip = new StepConfigTester(r).configRoundTrip(step);
        r.assertEqualDataBoundBeans(step, roundtrip);
    }

    @Test public void basicCloneAndUpdate() throws Exception {
        sampleRepo.init();
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
        r.createOnlineSlave(Label.get("remote"));
        p.setDefinition(new CpsFlowDefinition(
            "node('remote') {\n" +
            "    ws {\n" +
            "        git(url: $/" + sampleRepo + "/$, poll: false, changelog: false)\n" +
            "        archive '**'\n" +
            "    }\n" +
            "}"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("Cloning the remote Git repository", b); // GitSCM.retrieveChanges
        assertTrue(b.getArtifactManager().root().child("file").isFile());
        sampleRepo.write("nextfile", "");
        sampleRepo.git("add", "nextfile");
        sampleRepo.git("commit", "--message=next");
        b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("Fetching changes from the remote Git repository", b); // GitSCM.retrieveChanges
        assertTrue(b.getArtifactManager().root().child("nextfile").isFile());
    }

    @Test public void changelogAndPolling() throws Exception {
        sampleRepo.init();
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
        p.addTrigger(new SCMTrigger("")); // no schedule, use notifyCommit only
        r.createOnlineSlave(Label.get("remote"));
        p.setDefinition(new CpsFlowDefinition(
            "node('remote') {\n" +
            "    ws {\n" +
            "        git($/" + sampleRepo + "/$)\n" +
            "    }\n" +
            "}"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("Cloning the remote Git repository", b);
        sampleRepo.write("nextfile", "");
        sampleRepo.git("add", "nextfile");
        sampleRepo.git("commit", "--message=next");
        sampleRepo.notifyCommit(r);
        b = p.getLastBuild();
        assertEquals(2, b.number);
        r.assertLogContains("Fetching changes from the remote Git repository", b);
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
        assertEquals(1, changeSets.size());
        ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
        assertEquals(b, changeSet.getRun());
        assertEquals("git", changeSet.getKind());
        Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
        assertTrue(iterator.hasNext());
        ChangeLogSet.Entry entry = iterator.next();
        assertEquals("[nextfile]", entry.getAffectedPaths().toString());
        assertFalse(iterator.hasNext());
    }

    @Test public void multipleSCMs() throws Exception {
        sampleRepo.init();
        otherRepo.git("init");
        otherRepo.write("otherfile", "");
        otherRepo.git("add", "otherfile");
        otherRepo.git("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
        p.addTrigger(new SCMTrigger(""));
        p.setQuietPeriod(3); // so it only does one build
        p.setDefinition(new CpsFlowDefinition(
            "node {\n" +
            "    ws {\n" +
            "        dir('main') {\n" +
            "            git($/" + sampleRepo + "/$)\n" +
            "        }\n" +
            "        dir('other') {\n" +
            "            git($/" + otherRepo + "/$)\n" +
            "        }\n" +
            "        archive '**'\n" +
            "    }\n" +
            "}"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        VirtualFile artifacts = b.getArtifactManager().root();
        assertTrue(artifacts.child("main/file").isFile());
        assertTrue(artifacts.child("other/otherfile").isFile());
        sampleRepo.write("file2", "");
        sampleRepo.git("add", "file2");
        sampleRepo.git("commit", "--message=file2");
        otherRepo.write("otherfile2", "");
        otherRepo.git("add", "otherfile2");
        otherRepo.git("commit", "--message=otherfile2");
        sampleRepo.notifyCommit(r);
        otherRepo.notifyCommit(r);
        b = p.getLastBuild();
        assertEquals(2, b.number);
        artifacts = b.getArtifactManager().root();
        assertTrue(artifacts.child("main/file2").isFile());
        assertTrue(artifacts.child("other/otherfile2").isFile());
        Iterator<? extends SCM> scms = p.getSCMs().iterator();
        assertTrue(scms.hasNext());
        assertEquals(sampleRepo.toString(), ((GitSCM) scms.next()).getRepositories().get(0).getURIs().get(0).toString());
        assertTrue(scms.hasNext());
        assertEquals(otherRepo.toString(), ((GitSCM) scms.next()).getRepositories().get(0).getURIs().get(0).toString());
        assertFalse(scms.hasNext());
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
        assertEquals(2, changeSets.size());
        ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
        assertEquals(b, changeSet.getRun());
        assertEquals("git", changeSet.getKind());
        Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
        assertTrue(iterator.hasNext());
        ChangeLogSet.Entry entry = iterator.next();
        assertEquals("[file2]", entry.getAffectedPaths().toString());
        assertFalse(iterator.hasNext());
        changeSet = changeSets.get(1);
        iterator = changeSet.iterator();
        assertTrue(iterator.hasNext());
        entry = iterator.next();
        assertEquals("[otherfile2]", entry.getAffectedPaths().toString());
        assertFalse(iterator.hasNext());
    }

    @Issue("JENKINS-29326")
    @Test
    public void identicalGitSCMs() throws Exception {
        otherRepo.git("init");
        otherRepo.write("firstfile", "");
        otherRepo.git("add", "firstfile");
        otherRepo.git("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "    dir('main') {\n" +
                        "        git($/" + otherRepo + "/$)\n" +
                        "    }\n" +
                        "    dir('other') {\n" +
                        "        git($/" + otherRepo + "/$)\n" +
                        "    }\n" +
                        "}"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(1, b.getActions(BuildData.class).size());
        assertEquals(1, b.getActions(GitTagAction.class).size());
        assertEquals(0, b.getChangeSets().size());
        assertEquals(1, p.getSCMs().size());

        otherRepo.write("secondfile", "");
        otherRepo.git("add", "secondfile");
        otherRepo.git("commit", "--message=second");
        WorkflowRun b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(1, b2.getActions(BuildData.class).size());
        assertEquals(1, b2.getActions(GitTagAction.class).size());
        assertEquals(1, b2.getChangeSets().size());
        assertFalse(b2.getChangeSets().get(0).isEmptySet());
        assertEquals(1, p.getSCMs().size());
    }

    @Test public void commitToWorkspace() throws Exception {
        sampleRepo.init();
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
            "def rungit(cmd) {def gitcmd = \"git ${cmd}\"; if (isUnix()) {sh gitcmd} else {bat gitcmd}}\n" +
            "node {\n" +
            "  git url: $/" + sampleRepo + "/$\n" +
            "  writeFile file: 'file', text: 'edited by build'\n" +
            "  rungit 'commit --all --message=edits'\n" +
            "  rungit 'show master'\n" +
            "}"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("+edited by build", b);
    }

}
