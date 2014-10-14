package org.jenkinsci.plugins.workflow.cps.steps

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class EvaluateStepTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    /**
     * First test case for {@code evaluateWorkspaceScript}
     */
    @Test public void basics() throws Exception {
        def p = r.jenkins.createProject(WorkflowJob.class, "p");;
        p.definition = new CpsFlowDefinition("""
node {
  sh 'echo "println(42)" > test.groovy'
  evaluateWorkspaceScript 'test.groovy'
}
""");
        r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.interactiveBreak()
    }

}
