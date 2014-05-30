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

import hudson.model.Label;
import hudson.scm.ChangeLogSet;
import hudson.triggers.SCMTrigger;
import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

public class GitStepTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File sampleRepo;

    private void git(String... cmds) throws Exception {
        List<String> args = new ArrayList<String>();
        args.add("git");
        args.addAll(Arrays.asList(cmds));
        assertEquals(0, new ProcessBuilder(args).inheritIO().directory(sampleRepo).start().waitFor());
    }

    @Before public void sampleRepo() throws Exception {
        sampleRepo = tmp.newFolder();
        git("init");
        FileUtils.touch(new File(sampleRepo, "file"));
        git("add", "file");
        git("commit", "--message=init");
    }
    
    @Test public void basicCloneAndUpdate() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
        r.createOnlineSlave(Label.get("remote"));
        p.setDefinition(new CpsFlowDefinition(
            "with.node('remote') {\n" +
            "    with.ws {\n" +
            "        steps.git(url: '" + sampleRepo + "', poll: false, changelog: false)\n" +
            "        sh 'for f in *; do echo PRESENT: $f; done'\n" +
            "    }\n" +
            "}"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("Cloning the remote Git repository", b); // GitSCM.retrieveChanges
        r.assertLogContains("PRESENT: file", b);
        FileUtils.touch(new File(sampleRepo, "nextfile"));
        git("add", "nextfile");
        git("commit", "--message=next");
        b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("Fetching changes from the remote Git repository", b); // GitSCM.retrieveChanges
        r.assertLogContains("PRESENT: nextfile", b);
    }

    @Ignore("WiP")
    @Test public void changelogAndPolling() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
        p.addTrigger(new SCMTrigger("")); // no schedule, use notifyCommit only
        r.createOnlineSlave(Label.get("remote"));
        p.setDefinition(new CpsFlowDefinition(
            "with.node('remote') {\n" +
            "    with.ws {\n" +
            "        steps.git(url: '" + sampleRepo + "')\n" +
            "    }\n" +
            "}"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("Cloning the remote Git repository", b);
        FileUtils.touch(new File(sampleRepo, "nextfile"));
        git("add", "nextfile");
        git("commit", "--message=next");
        System.out.println(r.createWebClient().goTo("git/notifyCommit?url=" + URLEncoder.encode(sampleRepo.getAbsolutePath(), "UTF-8"), "text/plain").getWebResponse().getContentAsString());
        r.waitUntilNoActivity();
        b = p.getLastBuild();
        assertEquals(2, b.number);
        r.assertLogContains("Fetching changes from the remote Git repository", b);
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeLogs = b.getChangeLogs();
        assertEquals(1, changeLogs.size());
        ChangeLogSet<? extends ChangeLogSet.Entry> changeLog = changeLogs.get(0);
        assertEquals(b, changeLog.getBuild());
        assertEquals("git", changeLog.getKind());
        Iterator<? extends ChangeLogSet.Entry> iterator = changeLog.iterator();
        assertTrue(iterator.hasNext());
        ChangeLogSet.Entry entry = iterator.next();
        assertEquals("[nextfile]", entry.getAffectedPaths().toString());
        assertFalse(iterator.hasNext());
    }


}