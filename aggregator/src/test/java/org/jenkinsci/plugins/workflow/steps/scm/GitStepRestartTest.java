/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

import hudson.model.Label;
import hudson.scm.ChangeLogSet;
import hudson.triggers.SCMTrigger;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class GitStepRestartTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule r = new RestartableJenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Issue("JENKINS-26761")
    @Test public void checkoutsRestored() throws Exception {
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                sampleRepo.init();
                WorkflowJob p = r.j.jenkins.createProject(WorkflowJob.class, "p");
                p.addTrigger(new SCMTrigger(""));
                r.j.createOnlineSlave(Label.get("remote"));
                p.setDefinition(new CpsFlowDefinition(
                    "node('remote') {\n" +
                    "    ws {\n" +
                    "        git($/" + sampleRepo + "/$)\n" +
                    "    }\n" +
                    "}"));
                p.save();
                WorkflowRun b = r.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                r.j.assertLogContains("Cloning the remote Git repository", b);
                FileUtils.copyFile(new File(b.getRootDir(), "build.xml"), System.out);
            }
        });
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = r.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                r.j.createOnlineSlave(Label.get("remote"));
                sampleRepo.write("nextfile", "");
                sampleRepo.git("add", "nextfile");
                sampleRepo.git("commit", "--message=next");
                sampleRepo.notifyCommit(r.j);
                WorkflowRun b = p.getLastBuild();
                assertEquals(2, b.number);
                r.j.assertLogContains("Cloning the remote Git repository", b); // new slave, new workspace
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
        });
    }

}
