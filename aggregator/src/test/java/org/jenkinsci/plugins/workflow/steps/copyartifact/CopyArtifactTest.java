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

import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.List;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class CopyArtifactTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void test_simpleUntriggeredCopy() throws Exception {
        // create "project_1" with an archived artifact...
        WorkflowJob project_1 = createWorkflow("project_1",
                "sh 'echo hello > hello.txt'; " +
                "step([$class: 'ArtifactArchiver', artifacts: 'hello.txt', fingerprint: true])");
        WorkflowRun b = jenkinsRule.assertBuildStatusSuccess(project_1.scheduleBuild2(0));
        assertArtifactInArchive(b);

        // Now lets try copy the artifact from "project_1" to "project_2"
        WorkflowJob project_2 = createWorkflow("project_2",
                "step([$class: 'CopyArtifact', projectName: 'project_1', filter: 'hello.txt']); " +
                "step([$class: 'ArtifactArchiver', artifacts: 'hello.txt', fingerprint: true]);");
        b = jenkinsRule.assertBuildStatusSuccess(project_2.scheduleBuild2(0));
        assertArtifactInArchive(b);
    }

    @Test
    public void test_upstreamCopyFromDownstream() throws Exception {
        // create "project_1" with an archived artifact...
        createWorkflow("project_1",
                "sh 'echo hello > hello.txt'; " +
                "step([$class: 'ArtifactArchiver', artifacts: 'hello.txt'])");

        // Create "project_2", which triggers build of "project_1"
        WorkflowJob project_2 = createWorkflow("project_2",
                "build ('project_1'); " +
                "step ([$class: 'CopyArtifact', projectName: 'project_1', filter: 'hello.txt']); " +
                "step ([$class: 'ArtifactArchiver', artifacts: 'hello.txt', fingerprint: true]);");

        // run project_2 and check that was able to copy from project_1
        WorkflowRun b = jenkinsRule.assertBuildStatusSuccess(project_2.scheduleBuild2(0));
        assertArtifactInArchive(b);
    }

    @Test
    public void test_downstreamCopyFromUpstream() throws Exception {
        // create "project_1" with an archived artifact...
        WorkflowJob project_1 = createWorkflow("project_1",
                "sh 'echo hello > hello.txt'; " +
                "step([$class: 'ArtifactArchiver', artifacts: 'hello.txt']); " +
                "build ('project_2'); ");

        // Create "project_2", which triggers build of "project_1"
        // I'm sure there's a sweeter way of passing in the build number via a parameter
        WorkflowJob project_2 = createWorkflow("project_2",
                "step ([$class: 'CopyArtifact', projectName: 'project_1', filter: 'hello.txt', selector: [$class: 'SpecificBuildSelector', buildNumber: '#1']]); " +
                "step ([$class: 'ArtifactArchiver', artifacts: 'hello.txt', fingerprint: true]); ");

        // run project_1 and check that project_2 was triggered and copied hello.txt
        WorkflowRun b = jenkinsRule.assertBuildStatusSuccess(project_1.scheduleBuild2(0));
        assertArtifactInArchive(b);

        assertArtifactInArchive(project_2.getLastBuild());
    }



    private void assertArtifactInArchive(Run b) {
        List<WorkflowRun.Artifact> artifacts = b.getArtifacts();
        Assert.assertEquals(1, artifacts.size());
        Assert.assertEquals("hello.txt", artifacts.get(0).relativePath);
    }

    private WorkflowJob createWorkflow(String name, String script) throws IOException {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, name);
        job.setDefinition(new CpsFlowDefinition("node {" + script + "}", true));
        return job;
    }
}
