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
import java.io.File;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.plugins.git.GitSCMSource;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.global.WorkflowLibRepository;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.scm.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.steps.scm.GitStep;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class SCMVarTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();
    @Rule public GitSampleRepoRule sampleGitRepo = new GitSampleRepoRule();

    @Test public void scmPickle() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                sampleGitRepo.init();
                sampleGitRepo.write("Jenkinsfile", "def _scm = scm; semaphore 'wait'; node {checkout _scm; echo readFile('file')}");
                sampleGitRepo.write("file", "initial content");
                sampleGitRepo.git("add", "Jenkinsfile");
                sampleGitRepo.git("commit", "--all", "--message=flow");
                WorkflowMultiBranchProject mp = story.j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
                mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleGitRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
                WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "master");
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
                SCMBinderTest.assertRevisionAction(b1);
            }
        });
    }

    @Issue("JENKINS-30222")
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
                    "    checkout scm\n" +
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
                WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "master");
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("loaded resource content", b);
            }
        });
    }

    @Issue("JENKINS-31386")
    @Test public void standaloneProject() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                sampleGitRepo.init();
                sampleGitRepo.write("Jenkinsfile", "node {checkout scm; echo readFile('file')}");
                sampleGitRepo.write("file", "some content");
                sampleGitRepo.git("add", "Jenkinsfile");
                sampleGitRepo.git("commit", "--all", "--message=flow");
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsScmFlowDefinition(new GitStep(sampleGitRepo.toString()).createSCM(), "Jenkinsfile"));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("some content", b);
            }
        });
    }

}
