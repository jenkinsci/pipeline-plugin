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

import hudson.ExtensionList;
import hudson.model.RootAction;
import hudson.plugins.mercurial.MercurialInstallation;
import hudson.plugins.mercurial.MercurialSCMSource;
import hudson.tools.ToolProperty;
import java.io.File;
import java.util.Collections;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.impl.subversion.SubversionSCMSource;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.cps.global.WorkflowLibRepository;
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
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class SCMBinderTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();
    @Rule public GitSampleRepoRule sampleGitRepo = new GitSampleRepoRule();
    @Rule public SubversionSampleRepoRule sampleSvnRepo = new SubversionSampleRepoRule();
    @Rule public MercurialSampleRepoRule sampleHgRepo = new MercurialSampleRepoRule();

    @Test public void scmPickle() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                sampleGitRepo.init();
                sampleGitRepo.write("Jenkinsfile", "semaphore 'wait'; node {checkout scm; echo readFile('file')}");
                sampleGitRepo.write("file", "initial content");
                sampleGitRepo.git("add", "Jenkinsfile");
                sampleGitRepo.git("commit", "--all", "--message=flow");
                WorkflowMultiBranchProject mp = story.j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
                mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleGitRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
                WorkflowJob p = WorkflowMultiBranchProjectTest.findBranchProject(mp, "master");
                SemaphoreStep.waitForStart("wait/1", null);
                WorkflowRun b1 = p.getLastBuild();
                assertNotNull(b1);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                SemaphoreStep.success("wait/1", null);
                WorkflowJob p = story.j.jenkins.getItemByFullName("p/master", WorkflowJob.class);
                assertNotNull(p);
                WorkflowRun b1 = p.getLastBuild();
                assertNotNull(b1);
                assertEquals(1, b1.getNumber());
                story.j.assertLogContains("initial content", story.j.waitForCompletion(b1));
            }
        });
    }

    @Test public void exactRevisionGit() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                sampleGitRepo.init();
                ScriptApproval sa = ScriptApproval.get();
                sa.approveSignature("staticField hudson.model.Items XSTREAM2");
                sa.approveSignature("method com.thoughtworks.xstream.XStream toXML java.lang.Object");
                sampleGitRepo.write("Jenkinsfile", "echo hudson.model.Items.XSTREAM2.toXML(scm); semaphore 'wait'; node {checkout scm; echo readFile('file')}");
                sampleGitRepo.write("file", "initial content");
                sampleGitRepo.git("add", "Jenkinsfile");
                sampleGitRepo.git("commit", "--all", "--message=flow");
                WorkflowMultiBranchProject mp = story.j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
                mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleGitRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
                WorkflowJob p = WorkflowMultiBranchProjectTest.findBranchProject(mp, "master");
                SemaphoreStep.waitForStart("wait/1", null);
                WorkflowRun b1 = p.getLastBuild();
                assertNotNull(b1);
                assertEquals(1, b1.getNumber());
                sampleGitRepo.write("Jenkinsfile", "node {checkout scm; echo readFile('file').toUpperCase()}");
                sa.approveSignature("method java.lang.String toUpperCase");
                sampleGitRepo.write("file", "subsequent content");
                sampleGitRepo.git("commit", "--all", "--message=tweaked");
                SemaphoreStep.success("wait/1", null);
                WorkflowRun b2 = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                assertEquals(2, b2.getNumber());
                story.j.assertLogContains("initial content", story.j.waitForCompletion(b1));
                story.j.assertLogContains("SUBSEQUENT CONTENT", b2);
            }
        });
    }

    @Test public void exactRevisionSubversion() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                sampleSvnRepo.init();
                ScriptApproval sa = ScriptApproval.get();
                sa.approveSignature("staticField hudson.model.Items XSTREAM2");
                sa.approveSignature("method com.thoughtworks.xstream.XStream toXML java.lang.Object");
                sampleSvnRepo.write("Jenkinsfile", "echo hudson.model.Items.XSTREAM2.toXML(scm); semaphore 'wait'; node {checkout scm; echo readFile('file')}");
                sampleSvnRepo.write("file", "initial content");
                sampleSvnRepo.svn("add", "Jenkinsfile");
                sampleSvnRepo.svn("commit", "--message=flow");
                WorkflowMultiBranchProject mp = story.j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
                mp.getSourcesList().add(new BranchSource(new SubversionSCMSource(null, sampleSvnRepo.prjUrl(), null, null, null), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
                WorkflowJob p = WorkflowMultiBranchProjectTest.findBranchProject(mp, "trunk");
                SemaphoreStep.waitForStart("wait/1", null);
                WorkflowRun b1 = p.getLastBuild();
                assertNotNull(b1);
                assertEquals(1, b1.getNumber());
                sampleSvnRepo.write("Jenkinsfile", "node {checkout scm; echo readFile('file').toUpperCase()}");
                sa.approveSignature("method java.lang.String toUpperCase");
                sampleSvnRepo.write("file", "subsequent content");
                sampleSvnRepo.svn("commit", "--message=tweaked");
                SemaphoreStep.success("wait/1", null);
                WorkflowRun b2 = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                assertEquals(2, b2.getNumber());
                story.j.assertLogContains("initial content", story.j.waitForCompletion(b1));
                story.j.assertLogContains("SUBSEQUENT CONTENT", b2);
            }
        });
    }

    @Test public void exactRevisionMercurial() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                sampleHgRepo.init();
                ScriptApproval sa = ScriptApproval.get();
                sa.approveSignature("staticField hudson.model.Items XSTREAM2");
                sa.approveSignature("method com.thoughtworks.xstream.XStream toXML java.lang.Object");
                sampleHgRepo.write("Jenkinsfile", "echo hudson.model.Items.XSTREAM2.toXML(scm); semaphore 'wait'; node {checkout scm; echo readFile('file')}");
                sampleHgRepo.write("file", "initial content");
                sampleHgRepo.hg("commit", "--addremove", "--message=flow");
                WorkflowMultiBranchProject mp = story.j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
                String instName = "caching";
                story.j.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(
                        new MercurialInstallation(instName, "", "hg", false, true, false, null, Collections.<ToolProperty<?>> emptyList()));
                /* Does not actually seem to be necessary:
                { // TODO MercurialSCM.CACHE_LOCAL_REPOS = true;
                    Field CACHE_LOCAL_REPOS = MercurialSCM.class.getDeclaredField("CACHE_LOCAL_REPOS");
                    CACHE_LOCAL_REPOS.setAccessible(true);
                    CACHE_LOCAL_REPOS.set(null, true);
                }
                */
                mp.getSourcesList().add(new BranchSource(new MercurialSCMSource(null, instName, sampleHgRepo.fileUrl(), null, null, null, null, null, true), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
                WorkflowJob p = WorkflowMultiBranchProjectTest.findBranchProject(mp, "default");
                SemaphoreStep.waitForStart("wait/1", null);
                WorkflowRun b1 = p.getLastBuild();
                assertNotNull(b1);
                assertEquals(1, b1.getNumber());
                sampleHgRepo.write("Jenkinsfile", "node {checkout scm; echo readFile('file').toUpperCase()}");
                sa.approveSignature("method java.lang.String toUpperCase");
                sampleHgRepo.write("file", "subsequent content");
                sampleHgRepo.hg("commit", "--message=tweaked");
                SemaphoreStep.success("wait/1", null);
                WorkflowRun b2 = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                assertEquals(2, b2.getNumber());
                story.j.assertLogContains("initial content", story.j.waitForCompletion(b1));
                story.j.assertLogContains("SUBSEQUENT CONTENT", b2);
            }
        });
    }

    @Test public void globalVariable() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                // Set up a standardJob definition:
                WorkflowLibRepository repo = ExtensionList.lookup(RootAction.class).get(WorkflowLibRepository.class);
                File vars = new File(repo.workspace, /*UserDefinedGlobalVariable.PREFIX*/ "vars");
                vars.mkdirs();
                // TODO is this safe to add to generic-whitelist? (Why are global libs even being run through the sandbox to begin with?)
                ScriptApproval.get().approveSignature("method groovy.lang.Closure getOwner");
                FileUtils.writeStringToFile(new File(vars, "standardJob.groovy"),
                    "def call(body) {\n" +
                    "  def config = [:]\n" +
                    "  body.resolveStrategy = Closure.DELEGATE_FIRST\n" +
                    "  body.delegate = config\n" +
                    "  body()\n" +
                    "  node {\n" +
                    "    checkout body.owner.scm\n" +
                    "    echo \"loaded ${readFile config.file}\"\n" +
                    "  }\n" +
                    "}\n");
                // Then a project using it:
                sampleGitRepo.init();
                sampleGitRepo.write("Jenkinsfile", "standardJob {file = 'resource'}");
                sampleGitRepo.write("resource", "resource content");
                sampleGitRepo.git("add", "Jenkinsfile");
                sampleGitRepo.git("add", "resource");
                sampleGitRepo.git("commit", "--all", "--message=flow");
                // And run:
                WorkflowMultiBranchProject mp = story.j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
                mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleGitRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
                WorkflowJob p = WorkflowMultiBranchProjectTest.findBranchProject(mp, "master");
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("loaded resource content", b);
            }
        });
    }

}
