package org.jenkinsci.plugins.workflow.steps;

import hudson.model.Result;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable.Row;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.*;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class TimeoutStepTest extends Assert {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    /**
     * The simplest possible timeout step ever.
     */
    @Test
    public void basic() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "node { timeout(time:5, unit:'SECONDS') { sleep 10; echo 'NotHere' } }"));
                WorkflowRun b = story.j.assertBuildStatus(Result.ABORTED, p.scheduleBuild2(0).get());
                story.j.assertLogNotContains("NotHere", b);
            }
        });
    }

    @Test
    public void killingParallel() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  timeout(time:5, unit:'SECONDS') {\n"
                        + "    parallel(\n"
                        + "      a: { echo 'ShouldBeHere1'; sleep 10; echo 'NotHere' },\n"
                        + "      b: { echo 'ShouldBeHere2'; sleep 10; echo 'NotHere' },\n"
                        + "    );\n"
                        + "    echo 'NotHere'\n"
                        + "  }\n"
                        + "  echo 'NotHere'\n"
                        + "}\n"));
                WorkflowRun b = story.j.assertBuildStatus(/* TODO JENKINS-25894 should really be ABORTED */Result.FAILURE, p.scheduleBuild2(0).get());

                // make sure things that are supposed to run do, and things that are NOT supposed to run do not.
                story.j.assertLogNotContains("NotHere", b);
                story.j.assertLogContains("ShouldBeHere1", b);
                story.j.assertLogContains("ShouldBeHere2", b);

                // we expect every sleep step to have failed
                FlowGraphTable t = new FlowGraphTable(b.getExecution());
                t.build();
                for (Row r : t.getRows()) {
                    if (r.getNode() instanceof StepAtomNode) {
                        StepAtomNode a = (StepAtomNode) r.getNode();
                        if (a.getDescriptor().getClass() == SleepStep.DescriptorImpl.class) {
                            assertTrue(a.getAction(ErrorAction.class) != null);
                        }
                    }
                }
            }
        });
    }

    @Issue("JENKINS-26163")
    @Test
    public void restarted() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "restarted");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  timeout(time: 15, unit: 'SECONDS') {\n"
                        + "    semaphore 'restarted'\n"
                        + "    sleep 999\n"
                        + "  }\n"
                        + "}\n"));
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("restarted/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("restarted", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                SemaphoreStep.success("restarted/1", null);
                story.j.assertBuildStatus(Result.ABORTED, story.j.waitForCompletion(b));
            }
        });
    }

    @Test
    public void timeIsConsumed() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "timeIsConsumed");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  timeout(time: 20, unit: 'SECONDS') {\n"
                        + "    sleep 10\n"
                        + "    semaphore 'timeIsConsumed'\n"
                        + "    sleep 10\n"
                        + "  }\n"
                        + "}\n"));
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("timeIsConsumed/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("timeIsConsumed", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                SemaphoreStep.success("timeIsConsumed/1", null);
                WorkflowRun run = story.j.waitForCompletion(b);
                InterruptedBuildAction action = b.getAction(InterruptedBuildAction.class);
                assertNotNull(action);
                List<CauseOfInterruption> causes = action.getCauses();
                assertEquals(1, causes.size());
                assertEquals(TimeoutStepExecution.ExceededTimeout.class, causes.get(0).getClass());
                story.j.assertBuildStatus(Result.ABORTED, run);
            }
        });
    }

    // TODO: timeout inside parallel

}
