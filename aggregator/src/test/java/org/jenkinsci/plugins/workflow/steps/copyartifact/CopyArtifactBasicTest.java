/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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
package org.jenkinsci.plugins.workflow.steps.copyartifact;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class CopyArtifactBasicTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void copyFromProject() throws Exception {
        createArtifactInProject_1();

        // Now lets try copy the artifact from project_1 to project_2
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "project_2");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "    step([$class: 'CopyArtifact', projectName: 'project_1', filter: 'hello.txt'])\n" +
                        "    step([$class: 'ArtifactArchiver', artifacts: 'hello.txt', fingerprint: true])\n" +
                        "}",
                true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));

        // check the archive is in project_2
        assertArtifactInArchive(b);
    }

    public void createArtifactInProject_1() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "project_1");
        p.setDefinition(new CpsFlowDefinition("node {sh 'echo hello > hello.txt'; step([$class: 'ArtifactArchiver', artifacts: 'hello.txt', fingerprint: true])}", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));

        // check the archive is in project_1
        assertArtifactInArchive(b);
    }

    private void assertArtifactInArchive(WorkflowRun b) {
        List<WorkflowRun.Artifact> artifacts = b.getArtifacts();
        Assert.assertEquals(1, artifacts.size());
        Assert.assertEquals("hello.txt", artifacts.get(0).relativePath);
    }
}
