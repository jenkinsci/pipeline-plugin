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

import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.scm.SubversionSCM;
import hudson.triggers.SCMTrigger;
import java.util.Iterator;
import java.util.List;
import jenkins.util.VirtualFile;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class SubversionStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public SubversionSampleRepoRule sampleRepo = new SubversionSampleRepoRule();
    @Rule public SubversionSampleRepoRule otherRepo = new SubversionSampleRepoRule();

    @Test public void multipleSCMs() throws Exception {
        sampleRepo.init();
        otherRepo.basicInit();
        otherRepo.svn("co", otherRepo.trunkUrl(), ".");
        otherRepo.write("otherfile", "");
        otherRepo.svn("add", "otherfile");
        otherRepo.svn("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
        p.addTrigger(new SCMTrigger(""));
        p.setQuietPeriod(3); // so it only does one build
        p.setDefinition(new CpsFlowDefinition(
            "node {\n" +
            "    ws {\n" +
            "        dir('main') {\n" +
            "            svn(url: '" + sampleRepo.trunkUrl() + "')\n" +
            "        }\n" +
            "        dir('other') {\n" +
            "            svn(url: '" + otherRepo.trunkUrl() + "')\n" +
            "        }\n" +
            "        archive '**'\n" +
            "    }\n" +
            "}"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        VirtualFile artifacts = b.getArtifactManager().root();
        assertTrue(artifacts.child("main/file").isFile());
        assertTrue(artifacts.child("other/otherfile").isFile());
        sampleRepo.write("file2", "");
        sampleRepo.svn("add", "file2");
        sampleRepo.svn("commit", "--message=+file2");
        otherRepo.write("otherfile2", "");
        otherRepo.svn("add", "otherfile2");
        otherRepo.svn("commit", "--message=+otherfile2");
        sampleRepo.notifyCommit(r, "prj/trunk/file2");
        otherRepo.notifyCommit(r, "prj/trunk/otherfile2");
        r.waitUntilNoActivity();
        b = p.getLastBuild();
        assertEquals(2, b.number);
        artifacts = b.getArtifactManager().root();
        assertTrue(artifacts.child("main/file2").isFile());
        assertTrue(artifacts.child("other/otherfile2").isFile());
        Iterator<? extends SCM> scms = p.getSCMs().iterator();
        assertTrue(scms.hasNext());
        assertEquals(sampleRepo.trunkUrl().replaceFirst("/$", ""), ((SubversionSCM) scms.next()).getLocations()[0].getURL());
        assertTrue(scms.hasNext());
        assertEquals(otherRepo.trunkUrl().replaceFirst("/$", ""), ((SubversionSCM) scms.next()).getLocations()[0].getURL());
        assertFalse(scms.hasNext());
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
        assertEquals(2, changeSets.size());
        ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
        assertEquals(b, changeSet.getRun());
        assertEquals("svn", changeSet.getKind());
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
