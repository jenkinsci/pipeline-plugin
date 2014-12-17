package org.jenkinsci.plugins.workflow.steps.build;

import hudson.model.BooleanParameterDefinition;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
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
        foo.setDefinition(new CpsFlowDefinition("build 'test1'"));

        QueueTaskFuture<WorkflowRun> q = foo.scheduleBuild2(0);
        j.assertBuildStatusSuccess(q);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void buildFolderProject() throws Exception {
        MockFolder dir1 = j.createFolder("dir1");
        FreeStyleProject downstream = dir1.createProject(FreeStyleProject.class, "downstream");
        downstream.getBuildersList().add(new Shell("echo 'Hello World'"));

        MockFolder dir2 = j.createFolder("dir2");
        WorkflowJob upstream = dir2.createProject(WorkflowJob.class, "upstream");
        upstream.setDefinition(new CpsFlowDefinition("build '../dir1/downstream'"));

        QueueTaskFuture<WorkflowRun> q = upstream.scheduleBuild2(0);
        j.assertBuildStatusSuccess(q);
        assertEquals(1, downstream.getBuilds().size());
    }


    @Test
    public void buildParallelTests() throws Exception {
        FreeStyleProject p1 = j.createFreeStyleProject("test1");
        p1.getBuildersList().add(new Shell("echo 'Hello World'"));

        FreeStyleProject p2 = j.createFreeStyleProject("test2");
        p2.getBuildersList().add(new Shell("echo 'Hello World'"));




        WorkflowJob foo = j.jenkins.createProject(WorkflowJob.class, "foo");
        foo.setDefinition(new CpsFlowDefinition(StringUtils.join(Arrays.asList("parallel(test1: {\n" +
                "          build('test1');\n" +
                "        }, test2: {\n" +
                "          build('test2');\n" +
                "        })"), "\n"), true));

        QueueTaskFuture<WorkflowRun> q = foo.scheduleBuild2(0);
        j.assertBuildStatusSuccess(q);
    }


    @Test
    public void abortBuild() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("test1");
        p.getBuildersList().add(new Shell("sleep 6000"));

        WorkflowJob foo = j.jenkins.createProject(WorkflowJob.class, "foo");
        foo.setDefinition(new CpsFlowDefinition(StringUtils.join(Arrays.asList("build('test1');"), "\n")));

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

    @Test
    public void cancelBuildQueue() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("test1");
        p.getBuildersList().add(new Shell("sleep 6000"));

        WorkflowJob foo = j.jenkins.createProject(WorkflowJob.class, "foo");
        foo.setDefinition(new CpsFlowDefinition(StringUtils.join(Arrays.asList("build('test1');"), "\n")));

        j.jenkins.setNumExecutors(0); //should force freestyle build to remain in the queue?

        QueueTaskFuture<WorkflowRun> q = foo.scheduleBuild2(0);

        WorkflowRun b = q.getStartCondition().get();
        CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();
        e.waitForSuspension();

        Queue.Item[] items = j.jenkins.getQueue().getItems();
        assertEquals(1, items.length);
        j.jenkins.getQueue().cancel(items[0]);

        j.assertBuildStatus(Result.FAILURE,q.get());
    }

    @SuppressWarnings("deprecation")
    @Test public void triggerWorkflow() throws Exception {
        WorkflowJob us = j.jenkins.createProject(WorkflowJob.class, "us");
        us.setDefinition(new CpsFlowDefinition("build 'ds'"));
        WorkflowJob ds = j.jenkins.createProject(WorkflowJob.class, "ds");
        ds.setDefinition(new CpsFlowDefinition("echo 'OK'"));
        j.assertBuildStatusSuccess(us.scheduleBuild2(0));
        assertEquals(1, ds.getBuilds().size());
    }

    @Test public void parameters() throws Exception {
        WorkflowJob us = j.jenkins.createProject(WorkflowJob.class, "us");
        FreeStyleProject ds = j.jenkins.createProject(FreeStyleProject.class, "ds");
        ds.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("branch", "master"), new BooleanParameterDefinition("extra", false, null)));
        ds.getBuildersList().add(new Shell("echo branch=$branch extra=$extra"));
        us.setDefinition(new CpsFlowDefinition("build 'ds'"));
        WorkflowRun us1 = j.assertBuildStatusSuccess(us.scheduleBuild2(0));
        FreeStyleBuild ds1 = ds.getBuildByNumber(1);
        j.assertLogContains("branch=master extra=false", ds1);
        Cause.UpstreamCause cause = ds1.getCause(Cause.UpstreamCause.class);
        assertNotNull(cause);
        assertEquals(us1, cause.getUpstreamRun());
        // TODO JENKINS-26093 let us bind a simple Groovy map
        us.setDefinition(new CpsFlowDefinition("build job: 'ds', parameters: [new hudson.model.StringParameterValue('branch', 'release')]"));
        j.assertBuildStatusSuccess(us.scheduleBuild2(0));
        // TODO IIRC there is an open PR regarding automatic filling in of default parameter values; should that be used, or is BuildTriggerStepExecution responsible, or ParameterizedJobMixIn.scheduleBuild2?
        j.assertLogContains("branch=release extra=", ds.getBuildByNumber(2));
        us.setDefinition(new CpsFlowDefinition("build job: 'ds', parameters: [new hudson.model.StringParameterValue('branch', 'release'), new hudson.model.BooleanParameterValue('extra', true)]"));
        j.assertBuildStatusSuccess(us.scheduleBuild2(0));
        j.assertLogContains("branch=release extra=true", ds.getBuildByNumber(3));
    }

}
