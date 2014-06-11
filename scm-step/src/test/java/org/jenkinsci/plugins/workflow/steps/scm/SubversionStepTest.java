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
import hudson.scm.SubversionRepositoryStatus;
import hudson.scm.SubversionSCM;
import hudson.triggers.SCMTrigger;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

public class SubversionStepTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static void run(File cwd, String... cmds) throws Exception {
        assertEquals(0, new ProcessBuilder(cmds).inheritIO().directory(cwd).start().waitFor());
    }

    private static String uuid(String url) throws Exception {
        Process proc = new ProcessBuilder("svn", "info", "--xml", url).start();
        BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        Pattern p = Pattern.compile("<uuid>(.+)</uuid>");
        String line;
        while ((line = r.readLine()) != null) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                return m.group(1);
            }
        }
        throw new IllegalStateException("no output");
    }

    private void notifyCommit(String uuid, String path) throws Exception {
        // Mocking the web POST, with crumb, is way too hard, and get an IllegalStateException: STREAMED from doNotifyCommit’s getReader anyway.
        for (SubversionRepositoryStatus.Listener listener : Jenkins.getInstance().getExtensionList(SubversionRepositoryStatus.Listener.class)) {
            listener.onNotify(UUID.fromString(uuid), -1, Collections.singleton(path));
        }
    }

    @Test public void multipleSCMs() throws Exception {
        File sampleRepo = tmp.newFolder();
        String sampleRepoU = "file://" + sampleRepo; // TODO SVN rejects File.toUri syntax (requires blank authority field)
        run(sampleRepo, "svnadmin", "create", "--compatible-version=1.5", sampleRepo.getAbsolutePath());
        File sampleWc = tmp.newFolder();
        run(sampleWc, "svn", "co", sampleRepoU, ".");
        FileUtils.touch(new File(sampleWc, "file"));
        run(sampleWc, "svn", "add", "file");
        run(sampleWc, "svn", "commit", "--message=init");
        File otherRepo = tmp.newFolder();
        String otherRepoU = "file://" + otherRepo;
        run(otherRepo, "svnadmin", "create", "--compatible-version=1.5", otherRepo.getAbsolutePath());
        File otherWc = tmp.newFolder();
        run(otherWc, "svn", "co", otherRepoU, ".");
        FileUtils.touch(new File(otherWc, "otherfile"));
        run(otherWc, "svn", "add", "otherfile");
        run(otherWc, "svn", "commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
        p.addTrigger(new SCMTrigger(""));
        p.setQuietPeriod(3); // so it only does one build
        p.setDefinition(new CpsFlowDefinition(
            "with.node {\n" +
            "    with.ws {\n" +
            "        with.dir('main') {\n" +
            "            steps.svn(url: '" + sampleRepoU + "')\n" +
            "        }\n" +
            "        with.dir('other') {\n" +
            "            steps.svn(url: '" + otherRepoU + "')\n" +
            "        }\n" +
            "        sh 'for f in */*; do echo PRESENT: $f; done'\n" +
            "    }\n" +
            "}"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("PRESENT: main/file", b);
        r.assertLogContains("PRESENT: other/otherfile", b);
        FileUtils.touch(new File(sampleWc, "file2"));
        run(sampleWc, "svn", "add", "file2");
        run(sampleWc, "svn", "commit", "--message=+file2");
        FileUtils.touch(new File(otherWc, "otherfile2"));
        run(otherWc, "svn", "add", "otherfile2");
        run(otherWc, "svn", "commit", "--message=+otherfile2");
        notifyCommit(uuid(sampleRepoU), "file2");
        notifyCommit(uuid(otherRepoU), "otherfile2");
        r.waitUntilNoActivity();
        b = p.getLastBuild();
        assertEquals(2, b.number);
        r.assertLogContains("PRESENT: main/file2", b);
        r.assertLogContains("PRESENT: other/otherfile2", b);
        Iterator<? extends SCM> scms = p.getSCMs().iterator();
        assertTrue(scms.hasNext());
        assertEquals(sampleRepoU, ((SubversionSCM) scms.next()).getLocations()[0].getURL());
        assertTrue(scms.hasNext());
        assertEquals(otherRepoU, ((SubversionSCM) scms.next()).getLocations()[0].getURL());
        assertFalse(scms.hasNext());
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
        assertEquals(2, changeSets.size());
        ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
        assertEquals(b, changeSet.getRun());
        assertEquals("svn", changeSet.getKind());
        Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
        assertTrue(iterator.hasNext());
        ChangeLogSet.Entry entry = iterator.next();
        assertEquals("[/file2]", entry.getAffectedPaths().toString());
        assertFalse(iterator.hasNext());
        changeSet = changeSets.get(1);
        iterator = changeSet.iterator();
        assertTrue(iterator.hasNext());
        entry = iterator.next();
        assertEquals("[/otherfile2]", entry.getAffectedPaths().toString());
        assertFalse(iterator.hasNext());
    }

}