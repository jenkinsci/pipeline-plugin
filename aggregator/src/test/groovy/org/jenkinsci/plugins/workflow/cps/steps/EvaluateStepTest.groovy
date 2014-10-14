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
    @Test
    public void basics() throws Exception {
        def p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.definition = new CpsFlowDefinition("""
node {
  sh 'echo "println(21*2)" > test.groovy'
  println "control" // make sure that 'println' in groovy script works
  evaluateWorkspaceScript 'test.groovy'
}
""");
        def b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));

        println b.log
        assert b.log.contains("control\n");
        assert b.log.contains("42\n");
    }

    /**
     * "evaluate" call is supposed to yield a value
     */
    @Test
    public void evaluationResult() throws Exception {
        def p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.definition = new CpsFlowDefinition("""
node {
  sh 'echo "21*2" > test.groovy'
  def o = evaluateWorkspaceScript('test.groovy')
  println "output="+o
}
""");
        def b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));

        println b.log
        assert b.log.contains("output=42\n");
    }

}
