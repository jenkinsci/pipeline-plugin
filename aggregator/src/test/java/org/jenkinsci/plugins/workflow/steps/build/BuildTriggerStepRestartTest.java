package org.jenkinsci.plugins.workflow.steps.build;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import java.io.IOException;
import java.util.Arrays;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;

/**
 * @author Vivek Pandey
 */
public class BuildTriggerStepRestartTest extends Assert {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test
    public void restartBetweenJobs() throws IOException {

        story.addStep(new Statement() {
                          @Override
                          public void evaluate() throws Throwable {
                              story.j.jenkins.setNumExecutors(0);
                              FreeStyleProject p1 = story.j.createFreeStyleProject("test1");
                              WorkflowJob foo = story.j.jenkins.createProject(WorkflowJob.class, "foo");
                              foo.setDefinition(new CpsFlowDefinition("build 'test1'", true));
                              QueueTaskFuture<WorkflowRun> q = foo.scheduleBuild2(0);
                              WorkflowRun b = q.getStartCondition().get();
                              CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();
                              e.waitForSuspension();
                              assertFreeStyleProjectsInQueue(1);
                          }
                      }
        );

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                assertFreeStyleProjectsInQueue(1);
                story.j.jenkins.setNumExecutors(2);
            }
        });

        story.addStep(new Statement() {
                          @Override
                          public void evaluate() throws Throwable {
                              story.j.waitUntilNoActivity();
                              FreeStyleProject p1 = story.j.jenkins.getItemByFullName("test1", FreeStyleProject.class);
                              FreeStyleBuild r = p1.getLastBuild();
                              assertNotNull(r);
                              assertEquals(1, r.number);
                              assertEquals(Result.SUCCESS, r.getResult());
                              assertFreeStyleProjectsInQueue(0);
                              WorkflowJob foo = story.j.jenkins.getItemByFullName("foo", WorkflowJob.class);
                              assertNotNull(foo);
                              WorkflowRun r2 = foo.getLastBuild();
                              assertNotNull(r2);
                              story.j.assertBuildStatusSuccess(r2);
                          }
                      }
        );
    }

    private void assertFreeStyleProjectsInQueue(int count) {
        Queue.Item[] items = story.j.jenkins.getQueue().getItems();
        int actual = 0;
        for (Queue.Item item : items) {
            if (item.task instanceof FreeStyleProject) {
                actual++;
            }
        }
        assertEquals(Arrays.toString(items), count, actual);
    }

}
