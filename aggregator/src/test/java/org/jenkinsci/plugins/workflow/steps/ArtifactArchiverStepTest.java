package org.jenkinsci.plugins.workflow.steps;

import jenkins.util.VirtualFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;

/**
 * @author Kohsuke Kawaguchi
 */
public class ArtifactArchiverStepTest extends Assert {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Archive and unarchive file
     */
    @Test
    public void archive() throws Exception {
        // job setup
        WorkflowJob foo = j.jenkins.createProject(WorkflowJob.class, "foo");
        foo.setDefinition(new CpsFlowDefinition(StringUtils.join(Arrays.asList(
                "node {",
                "  writeFile text: 'hello world', file: 'msg'",
                "  archive 'm*'",
                "  unarchive(mapping:['msg':'msg.out'])",
                "  archive 'msg.out'",
                "}"), "\n")));


        // get the build going, and wait until workflow pauses
        WorkflowRun b = j.assertBuildStatusSuccess(foo.scheduleBuild2(0).get());

        VirtualFile archivedFile = b.getArtifactManager().root().child("msg.out");
        assertTrue(archivedFile.exists());
        assertEquals("hello world", IOUtils.toString(archivedFile.open()));
    }

    @Test public void unarchiveDir() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(StringUtils.join(Arrays.asList(
                "node {",
                "  writeFile text: 'one', file: 'a/1'; writeFile text: 'two', file: 'a/b/2'",
                "  archive 'a/'",
                "  dir('new') {",
                "    unarchive mapping: ['a/' : '.']",
                "    echo \"${readFile 'a/1'}/${readFile 'a/b/2'}\"",
                "  }",
                "}"), "\n")));
        WorkflowRun b = j.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
        VirtualFile archivedFile = b.getArtifactManager().root().child("a/b/2");
        assertTrue(archivedFile.exists());
        assertEquals("two", IOUtils.toString(archivedFile.open()));
        j.assertLogContains("one/two", b);
    }

}

