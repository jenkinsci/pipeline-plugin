package org.jenkinsci.plugins.workflow.steps.build;

import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.Shell;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.io.IOException;

/**
 * @author Vivek Pandey
 */
public class BuildTriggerStepRestartTest extends Assert {
    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test
    public void restartBetweenJobs() throws IOException {

        story.addStep(new Statement() {
                          @Override
                          public void evaluate() throws Throwable {
                              story.j.jenkins.setNumExecutors(0);
                              FreeStyleProject p1 = story.j.createFreeStyleProject("test1");
                              p1.getBuildersList().add(new Shell("echo 'Hello World'"));

                              WorkflowJob foo = story.j.jenkins.createProject(WorkflowJob.class, "foo");
                              foo.setDefinition(new CpsFlowDefinition("build 'test1'", true));


                              QueueTaskFuture<WorkflowRun> q = foo.scheduleBuild2(0);
                              WorkflowRun b = q.getStartCondition().get();
                              CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();
                              e.waitForSuspension();
                              assertEquals(1, story.j.jenkins.getQueue().getItems().length);
                          }
                      }
        );

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                assertEquals(1, story.j.jenkins.getQueue().getItems().length);
                story.j.jenkins.setNumExecutors(2);
            }
        });

        story.addStep(new Statement() {
                          @Override
                          public void evaluate() throws Throwable {
                              story.j.waitUntilNoActivity();
                              FreeStyleProject p1 = story.j.jenkins.getItemByFullName("test1", FreeStyleProject.class);
                              Run r = p1.getLastBuild();
                              assertNotNull(r);
                              assertEquals(1, r.number);
                              assertEquals(Result.SUCCESS, r.getResult());
                              assertEquals(0, story.j.jenkins.getQueue().getItems().length);
                              WorkflowJob foo = story.j.jenkins.getItemByFullName("foo", WorkflowJob.class);
                              assertNotNull(foo);
                              r = foo.getLastBuild();
                              assertNotNull(r);
                              story.j.assertBuildStatusSuccess(r);
                          }
                      }
        );
    }
}
