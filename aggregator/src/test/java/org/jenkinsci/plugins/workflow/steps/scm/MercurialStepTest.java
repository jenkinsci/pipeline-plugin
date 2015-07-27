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

import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.triggers.SCMTrigger;
import java.util.Iterator;
import java.util.List;
import jenkins.util.VirtualFile;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;

@For(GenericSCMStep.class) // formerly a dedicated MercurialStep
public class MercurialStepTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public MercurialSampleRepoRule sampleRepo = new MercurialSampleRepoRule();
    @Rule public MercurialSampleRepoRule otherRepo = new MercurialSampleRepoRule();

    @Test public void multipleSCMs() throws Exception {
        sampleRepo.init();
        otherRepo.hg("init");
        otherRepo.write("otherfile", "");
        otherRepo.hg("add", "otherfile");
        otherRepo.hg("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
        p.addTrigger(new SCMTrigger(""));
        p.setQuietPeriod(3); // so it only does one build
        p.setDefinition(new CpsFlowDefinition(
            "node {\n" +
            "    ws {\n" +
            "        dir('main') {\n" +
            "            checkout([$class: 'MercurialSCM', source: $/" + sampleRepo.fileUrl() + "/$])\n" +
            "        }\n" +
            "        dir('other') {\n" +
            "            checkout([$class: 'MercurialSCM', source: $/" + otherRepo.fileUrl() + "/$, clean: true])\n" +
            "            try {\n" + // TODO or use fileExists
            "                readFile 'unversioned'\n" +
            "                error 'unversioned did exist'\n" +
            "            } catch (FileNotFoundException x) {\n" +
            "                echo 'unversioned did not exist'\n" +
            "            }\n" +
            "            writeFile text: '', file: 'unversioned'\n" +
            "        }\n" +
            "        archive '**'\n" +
            "    }\n" +
            "}"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        VirtualFile artifacts = b.getArtifactManager().root();
        assertTrue(artifacts.child("main/file").isFile());
        assertTrue(artifacts.child("other/otherfile").isFile());
        r.assertLogContains("unversioned did not exist", b);
        sampleRepo.write("file2", "");
        sampleRepo.hg("add", "file2");
        sampleRepo.hg("commit", "--message=file2");
        otherRepo.write("otherfile2", "");
        otherRepo.hg("add", "otherfile2");
        otherRepo.hg("commit", "--message=otherfile2");
        sampleRepo.notifyCommit(r);
        otherRepo.notifyCommit(r);
        FileUtils.copyFile(p.getSCMTrigger().getLogFile(), System.out);
        b = r.assertBuildStatusSuccess(p.getLastBuild());
        assertEquals(2, b.number);
        artifacts = b.getArtifactManager().root();
        assertTrue(artifacts.child("main/file2").isFile());
        assertTrue(artifacts.child("other/otherfile2").isFile());
        r.assertLogContains("unversioned did not exist", b);
        Iterator<? extends SCM> scms = p.getSCMs().iterator();
        assertTrue(scms.hasNext());
        assertEquals(sampleRepo.fileUrl(), ((MercurialSCM) scms.next()).getSource());
        assertTrue(scms.hasNext());
        assertEquals(otherRepo.fileUrl(), ((MercurialSCM) scms.next()).getSource());
        assertFalse(scms.hasNext());
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
        assertEquals(2, changeSets.size());
        ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
        assertEquals(b, changeSet.getRun());
        assertEquals("hg", changeSet.getKind());
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

}
