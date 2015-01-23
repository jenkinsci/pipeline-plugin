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
import hudson.scm.NullSCM;
import hudson.scm.SCMRevisionState;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class CpsScmFlowDefinitionTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void basics() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsScmFlowDefinition(new SingleFileSCM("flow.groovy", "echo 'hello from SCM'"), "flow.groovy"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("hello from SCM", b);
        r.assertLogContains("Staging flow.groovy", b);
        FlowGraphWalker w = new FlowGraphWalker(b.getExecution());
        int workspaces = 0;
        FlowNode n;
        while ((n = w.next()) != null) {
            if (n.getAction(WorkspaceAction.class) != null) {
                workspaces++;
            }
        }
        assertEquals(1, workspaces);
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
