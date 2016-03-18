package org.jenkinsci.plugins.workflow.cps.steps;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class LoadStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();

    @Test
    public void basics() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
            "node {\n" +
            "  writeFile text: 'println(21*2)', file: 'test.groovy'\n" +
            "  println 'something printed'\n" +// make sure that 'println' in groovy script works
            "  load 'test.groovy'\n" +
            "}", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("something printed", b);
        r.assertLogContains("42", b);
    }

    /**
     * "evaluate" call is supposed to yield a value
     */
    @Test
    public void evaluationResult() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  writeFile text: '21*2', file: 'test.groovy'\n" +
                "  def o = load('test.groovy')\n" +
                "  println 'output=' + o\n" +
                "}"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("output=42", b);
    }

}
