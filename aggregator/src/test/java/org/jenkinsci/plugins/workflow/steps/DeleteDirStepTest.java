/*
 * The MIT License
 *
 * Copyright 2015 CLoudbees, Inc.
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

package org.jenkinsci.plugins.workflow.steps;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.FilePath;
import hudson.model.queue.QueueTaskFuture;

public class DeleteDirStepTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void testDeleteEmptyWorkspace() throws Exception {
        String workspace = runAndGetWorkspaceDir(
                "node {\n" +
                "  deleteDir()\n" + 
                "}");
        File f = new File(workspace);
        Assert.assertFalse("Workspace directory should no longer exist", f.exists());
    }

    @Test
    public void testDeleteTopLevelDir() throws Exception {
        String workspace = runAndGetWorkspaceDir(
                "node {\n" +
                "  writeFile file: 'f1', text: 'some text'\n" +
                "  writeFile file: 'f2', text: 'some text'\n" +
                "  writeFile file: '.hidden', text: 'some text'\n" +
                "  writeFile file: 'sub1/f1', text: 'some text'\n" +
                "  writeFile file: '.sub2/f2', text: 'some text'\n" +
                "  writeFile file: '.sub3/.hidden', text: 'some text'\n" +
                "  echo 'workspace is ---' + pwd() + '---'\n" + 
                "  deleteDir()\n" + 
                "}");
        File f = new File(workspace);
        Assert.assertFalse("Workspace directory should no longer exist", f.exists());
    }

    @Test
    public void testDeleteSubFolder() throws Exception {
        String workspace = runAndGetWorkspaceDir(
                "node {\n" +
                "  writeFile file: 'f1', text: 'some text'\n" +
                "  writeFile file: 'f2', text: 'some text'\n" +
                "  writeFile file: '.hidden', text: 'some text'\n" +
                "  writeFile file: 'sub1/f1', text: 'some text'\n" +
                "  writeFile file: '.sub2/f2', text: 'some text'\n" +
                "  writeFile file: '.sub3/.hidden', text: 'some text'\n" +
                "  echo 'workspace is ---' + pwd() + '---'\n" + 
                "  dir ('sub1') {\n" +
                "    deleteDir()\n" +
                "  }" + 
                "}");

        File f = new File(workspace);
        Assert.assertTrue("Workspace directory should still exist", f.exists());
        Assert.assertTrue("f1 should still exist", new File(f, "f1").exists());
        Assert.assertTrue("f1 should still exist", new File(f, "f2").exists());
        Assert.assertFalse("sub1 should not exist", new File(f, "sub1").exists());
        Assert.assertTrue(".sub2/f2 should still exist", new File(f, ".sub2/f2").exists());
        Assert.assertTrue(".sub3/.hidden should still exist", new File(f, ".sub3/.hidden").exists());
    }


    /**
     * Runs the given flow and returns the workspace used.
     * @param flow a flow definition.
     * @return the workspace used.
     */
    private String runAndGetWorkspaceDir(String flow) throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");

        p.setDefinition(new CpsFlowDefinition(flow));
        r.assertBuildStatusSuccess(p.scheduleBuild2(0));

        FilePath ws = r.jenkins.getWorkspaceFor(p);
        String workspace = ws.getRemote();
        Assert.assertNotNull("Unable to locate workspace", workspace);
        return workspace;
    }
}
