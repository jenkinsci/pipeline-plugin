package org.jenkinsci.plugins.workflow.steps;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Kohsuke Kawaguchi
 */
public class TimeoutStepTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void basic() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "import java.util.concurrent.*; node { timeout(time:5, unit:TimeUnit.SECONDS) { sh 'sleep 10'; echo 'NotHere' } }"));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        r.assertLogNotContains("NotHere", b);
    }
}
