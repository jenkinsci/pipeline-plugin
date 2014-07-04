package org.jenkinsci.plugins.workflow.steps.build;

import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.Shell;
import org.apache.commons.lang.StringUtils;
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
import java.util.Arrays;

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
            }
        });

        story.addStep(new Statement() {
                          @Override
                          public void evaluate() throws Throwable {
                              FreeStyleProject p1 = story.j.createFreeStyleProject("test1");
                              p1.getBuildersList().add(new Shell("echo 'Hello World'"));

                              WorkflowJob foo = story.j.jenkins.createProject(WorkflowJob.class, "foo");
                              foo.setDefinition(new CpsFlowDefinition(StringUtils.join(Arrays.asList("steps.build('test1');"), "\n")));


                              QueueTaskFuture<WorkflowRun> q = foo.scheduleBuild2(0);
                              WorkflowRun b = q.getStartCondition().get();
                              CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();
                              e.waitForSuspension();
                              assertEquals(1,story.j.jenkins.getQueue().getItems().length);
                          }
                      }
        );

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                assertEquals(1,story.j.jenkins.getQueue().getItems().length);
                story.j.jenkins.setNumExecutors(2);
            }
        });

        story.addStep(new Statement() {
                          @Override
                          public void evaluate() throws Throwable {
                              assertEquals(1,story.j.jenkins.getQueue().getItems().length);
                              Run r = (Run)story.j.jenkins.getQueue().getItems()[0].getFuture().get();
                              assertEquals(Result.SUCCESS, r.getResult());
                          }
                      }
        );

    }
}
