package org.jenkinsci.plugins.workflow.steps.build;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
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
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

import java.util.Arrays;

/**
 * @author Vivek Pandey
 */
public class BuildTriggerStepTest extends Assert {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void buildTopLevelProject() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("test1");
        p.getBuildersList().add(new Shell("echo 'Hello World'"));


        WorkflowJob foo = j.jenkins.createProject(WorkflowJob.class, "foo");
        foo.setDefinition(new CpsFlowDefinition(StringUtils.join(Arrays.asList("steps.build('test1');"), "\n")));

        QueueTaskFuture<WorkflowRun> q = foo.scheduleBuild2(0);
        j.assertBuildStatusSuccess(q);
    }

    @Test
    public void buildFolderProject() throws Exception {
        MockFolder f = j.createFolder("proj1");
        FreeStyleProject p = f.createProject(FreeStyleProject.class, "test1");
        p.getBuildersList().add(new Shell("echo 'Hello World'"));


        WorkflowJob foo = j.jenkins.createProject(WorkflowJob.class, "foo");
        foo.setDefinition(new CpsFlowDefinition(StringUtils.join(Arrays.asList("steps.build('proj1/test1');"), "\n")));

        QueueTaskFuture<WorkflowRun> q = foo.scheduleBuild2(0);
        j.assertBuildStatusSuccess(q);
    }


    @Test
    public void buildParallelTests() throws Exception {
        FreeStyleProject p1 = j.createFreeStyleProject("test1");
        p1.getBuildersList().add(new Shell("echo 'Hello World'"));

        FreeStyleProject p2 = j.createFreeStyleProject("test2");
        p2.getBuildersList().add(new Shell("echo 'Hello World'"));




        WorkflowJob foo = j.jenkins.createProject(WorkflowJob.class, "foo");
        foo.setDefinition(new CpsFlowDefinition(StringUtils.join(Arrays.asList("parallel(test1: {\n" +
                "          steps.build('test1');\n" +
                "        }, test2: {\n" +
                "          steps.build('test2');\n" +
                "        })"), "\n")));

        QueueTaskFuture<WorkflowRun> q = foo.scheduleBuild2(0);
        j.assertBuildStatusSuccess(q);
    }


    @Test
    public void abortBuild() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("test1");
        p.getBuildersList().add(new Shell("sleep 6000"));

        WorkflowJob foo = j.jenkins.createProject(WorkflowJob.class, "foo");
        foo.setDefinition(new CpsFlowDefinition(StringUtils.join(Arrays.asList("steps.build('test1');"), "\n")));

        QueueTaskFuture<WorkflowRun> q = foo.scheduleBuild2(0);
        WorkflowRun b = q.getStartCondition().get();

        CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();
        e.waitForSuspension();

        FreeStyleBuild fb=null;
        while (fb==null) {
            fb = p.getBuildByNumber(1);
            Thread.sleep(10);
        }
        fb.getExecutor().interrupt();

        while(fb.isBuilding());

        assertEquals(Result.ABORTED, fb.getResult());
        j.assertBuildStatus(Result.FAILURE,q.get());
    }
}
