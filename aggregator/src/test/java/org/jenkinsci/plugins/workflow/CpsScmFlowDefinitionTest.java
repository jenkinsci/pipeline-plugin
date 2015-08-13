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

package org.jenkinsci.plugins.workflow;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.NullSCM;
import hudson.scm.SCMRevisionState;
import hudson.triggers.SCMTrigger;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.scm.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.steps.scm.GitStep;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class CpsScmFlowDefinitionTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Test public void basics() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsScmFlowDefinition(new SingleFileSCM("flow.groovy", "echo 'hello from SCM'"), "flow.groovy"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        // TODO currently the log text is in Run.log, but not on FlowStartNode/LogAction, so not visible from Workflow Steps etc.
        r.assertLogContains("hello from SCM", b);
        r.assertLogContains("Staging flow.groovy", b);
        FlowGraphWalker w = new FlowGraphWalker(b.getExecution());
        int workspaces = 0;
        for (FlowNode n : w) {
            if (n.getAction(WorkspaceAction.class) != null) {
                workspaces++;
            }
        }
        assertEquals(1, workspaces);
    }

    @Test public void changelogAndPolling() throws Exception {
        sampleRepo.init();
        sampleRepo.write("flow.groovy", "echo 'version one'");
        sampleRepo.git("add", "flow.groovy");
        sampleRepo.git("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.addTrigger(new SCMTrigger("")); // no schedule, use notifyCommit only
        p.setDefinition(new CpsScmFlowDefinition(new GitStep(sampleRepo.toString()).createSCM(), "flow.groovy"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("Cloning the remote Git repository", b);
        r.assertLogContains("version one", b);
        sampleRepo.write("flow.groovy", "echo 'version two'");
        sampleRepo.git("add", "flow.groovy");
        sampleRepo.git("commit", "--message=next");
        sampleRepo.notifyCommit(r);
        b = p.getLastBuild();
        assertEquals(2, b.number);
        r.assertLogContains("Fetching changes from the remote Git repository", b);
        r.assertLogContains("version two", b);
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
        assertEquals(1, changeSets.size());
        ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
        assertEquals(b, changeSet.getRun());
        assertEquals("git", changeSet.getKind());
        Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
        assertTrue(iterator.hasNext());
        ChangeLogSet.Entry entry = iterator.next();
        assertEquals("[flow.groovy]", entry.getAffectedPaths().toString());
        assertFalse(iterator.hasNext());
    }

    // TODO 1.599+ use standard version
    private static class SingleFileSCM extends NullSCM {
        private final String path;
        private final byte[] contents;
        SingleFileSCM(String path, String contents) throws UnsupportedEncodingException {
            this.path = path;
            this.contents = contents.getBytes("UTF-8");
        }
        @Override public void checkout(Run<?, ?> build, Launcher launcher, FilePath workspace, TaskListener listener, File changelogFile, SCMRevisionState baseline) throws IOException, InterruptedException {
            listener.getLogger().println("Staging " + path);
            OutputStream os = workspace.child(path).write();
            IOUtils.write(contents, os);
            os.close();
        }
        private Object writeReplace() {
            return new Object();
        }
    }

}
