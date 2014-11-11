package org.jenkinsci.plugins.workflow.steps;

import hudson.model.Result;
import org.apache.commons.lang.StringUtils;
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

    /**
     * The simplest possible timeout step ever.
     */
    @Test
    public void basic() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "import java.util.concurrent.*; node { timeout(time:5, unit:TimeUnit.SECONDS) { sh 'sleep 10'; echo 'NotHere' } }"));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        r.assertLogNotContains("NotHere", b);
    }

    @Test
    public void killingParallel() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(join(
                "import java.util.concurrent.*; node { ",
                    "timeout(time:5, unit:TimeUnit.SECONDS) { ",
                        "parallel(",
                            " a: { echo 'ShouldBeHere1'; sh 'sleep 10'; echo 'NotHere' }, ",
                            " b: { echo 'ShouldBeHere2'; sh 'sleep 10'; echo 'NotHere' }, ",
                        ");",
                        "echo 'NotHere'",
                    "}",
                    "echo 'NotHere'",
                "}")));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        r.assertLogNotContains("NotHere", b);
        r.assertLogContains("ShouldBeHere1",b);
        r.assertLogContains("ShouldBeHere2",b);
        r.interactiveBreak();
    }

    // TODO: timeout inside parallel

    public String join(String... args) {
        return StringUtils.join(args, "\n");
    }
}
