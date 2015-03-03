package org.jenkinsci.plugins.workflow.steps.input;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.BooleanParameterDefinition;
import hudson.model.Job;
import hudson.model.User;
import hudson.model.queue.QueueTaskFuture;

import java.util.Arrays;
import java.util.List;

import hudson.security.ACL;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStepExecution;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Kohsuke Kawaguchi
 */
public class InputStepTest extends Assert {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Try out a parameter.
     */
    @Test
    public void parameter() throws Exception {
        // job setup
        WorkflowJob foo = j.jenkins.createProject(WorkflowJob.class, "foo");
        foo.setDefinition(new CpsFlowDefinition(StringUtils.join(Arrays.asList(
                "echo('before');",
                "def x = input message:'Do you want chocolate?', id:'Icecream', ok: 'Purchase icecream', parameters: [[$class: 'BooleanParameterDefinition', name: 'chocolate', defaultValue: false, description: 'Favorite icecream flavor']];",
                "echo(\"after: ${x}\");"),"\n"),true));


        // get the build going, and wait until workflow pauses
        QueueTaskFuture<WorkflowRun> q = foo.scheduleBuild2(0);
        WorkflowRun b = q.getStartCondition().get();
        CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

        while (b.getAction(InputAction.class)==null) {
            e.waitForSuspension();
        }

        // make sure we are pausing at the right state that reflects what we wrote in the program
        InputAction a = b.getAction(InputAction.class);
        assertEquals(1, a.getExecutions().size());

        InputStepExecution is = a.getExecution("Icecream");
        assertEquals("Do you want chocolate?", is.getInput().getMessage());
        assertEquals(1, is.getInput().getParameters().size());

        j.assertEqualDataBoundBeans(is.getInput().getParameters().get(0), new BooleanParameterDefinition("chocolate", false, "Favorite icecream flavor"));

        // submit the input, and run workflow to the completion
        HtmlPage p = j.createWebClient().getPage(b, a.getUrlName());
        j.submit(p.getFormByName(is.getId()),"proceed");
        assertEquals(0, a.getExecutions().size());
        q.get();

        // make sure 'x' gets assigned to false
        System.out.println(b.getLog());
        assertTrue(b.getLog().contains("after: false"));
    }

    @Test
    @Issue("JENKINS-26363")
    public void test_cancel_run_by_input() throws Exception {
        JenkinsRule.WebClient webClient = j.createWebClient();
        JenkinsRule.DummySecurityRealm dummySecurityRealm = j.createDummySecurityRealm();
        GlobalMatrixAuthorizationStrategy authorizationStrategy = new GlobalMatrixAuthorizationStrategy();

        j.jenkins.setSecurityRealm(dummySecurityRealm);

        // Only give "alice" basic privs. That's normally not enough to Job.CANCEL, only for the fact that "alice"
        // is listed as the submitter.
        addUserWithPrivs("alice", authorizationStrategy);
        // Only give "bob" basic privs.  That's normally not enough to Job.CANCEL and "bob" is not the submitter,
        // so they should be rejected.
        addUserWithPrivs("bob", authorizationStrategy);
        // Give "charlie" basic privs + Job.CANCEL.  That should allow user3 cancel.
        addUserWithPrivs("charlie", authorizationStrategy);
        authorizationStrategy.add(Job.CANCEL, "charlie");

        j.jenkins.setAuthorizationStrategy(authorizationStrategy);

        final WorkflowJob foo = j.jenkins.createProject(WorkflowJob.class, "foo");
        ACL.impersonate(User.get("alice").impersonate(), new Runnable() {
            @Override
            public void run() {
                foo.setDefinition(new CpsFlowDefinition("input id: 'InputX', message: 'OK?', ok: 'Yes', submitter: 'alice'"));
            }
        });

        runAndAbort(webClient, foo, "alice", true);   // alice should work coz she's declared as 'submitter'
        runAndAbort(webClient, foo, "bob", false);    // bob shouldn't work coz he's not declared as 'submitter' and doesn't have Job.CANCEL privs
        runAndAbort(webClient, foo, "charlie", true); // charlie should work coz he has Job.CANCEL privs
    }

    private void runAndAbort(JenkinsRule.WebClient webClient, WorkflowJob foo, String loginAs, boolean expectAbortOk) throws Exception {
        // get the build going, and wait until workflow pauses
        QueueTaskFuture<WorkflowRun> queueTaskFuture = foo.scheduleBuild2(0);
        WorkflowRun run = queueTaskFuture.getStartCondition().get();
        CpsFlowExecution execution = (CpsFlowExecution) run.getExecutionPromise().get();

        while (run.getAction(InputAction.class) == null) {
            execution.waitForSuspension();
        }

        webClient.login(loginAs);

        InputAction inputAction = run.getAction(InputAction.class);
        InputStepExecution is = inputAction.getExecution("InputX");
        HtmlPage p = webClient.getPage(run, inputAction.getUrlName());

        try {
            j.submit(p.getFormByName(is.getId()), "abort");
            assertEquals(0, inputAction.getExecutions().size());
            queueTaskFuture.get();

            List<String> log = run.getLog(1000);
            System.out.println(log);
            assertTrue(expectAbortOk);
            assertEquals("Finished: ABORTED", log.get(log.size() - 1)); // Should be aborted
        } catch (Exception e) {
            List<String> log = run.getLog(1000);
            System.out.println(log);
            assertFalse(expectAbortOk);
            assertEquals("Yes or Abort", log.get(log.size() - 1));  // Should still be paused at input
        }
    }

    private void addUserWithPrivs(String username, GlobalMatrixAuthorizationStrategy authorizationStrategy) {
        authorizationStrategy.add(Jenkins.READ, username);
        authorizationStrategy.add(Jenkins.RUN_SCRIPTS, username);
        authorizationStrategy.add(Job.READ, username);
    }
}
