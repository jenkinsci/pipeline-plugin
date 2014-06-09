package org.jenkinsci.plugins.workflow.job;

import hudson.FilePath;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.ParallelStep;
import org.jenkinsci.plugins.workflow.cps.ParallelStep.ParallelLabelAction;
import org.jenkinsci.plugins.workflow.cps.ParallelStepException;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.durable_task.ShellStep;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable.Row;
import org.jenkinsci.plugins.workflow.test.steps.WatchYourStep;
import org.junit.Test;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.*;
import static java.util.concurrent.TimeUnit.*;

/**
 * Tests for {@link ParallelStep}.
 *
 * @author Kohsuke Kawaguchi
 */
public class ParallelStepTest extends SingleJobTestBase {

    private FlowGraphTable t;

    /**
     * The first baby step.
     */
    @Test
    public void minimumViableParallelRun() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                    "with.node {",
                    "  x = parallel( a: { steps.echo('echo a'); return 1; }, b: { steps.echo('echo b'); return 2; } )",
                    "  assert x.a==1",
                    "  assert x.b==2",
                    "}"
                )));

                startBuilding().get(); // 15, SECONDS);
                assertBuildCompletedSuccessfully();

                buildTable();
                shouldHaveParallelStepsInTheOrder("a", "b");
            }
        });
    }

    /**
     * Failure in a branch will cause the join to fail.
     */
    @Test
    public void failure_in_subflow_will_cause_join_to_fail() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                    "import "+SimulatedFailureForRetry.class.getName(),
                    "import "+ParallelStepException.class.getName(),

                    "with.node {",
                    "  try {",
                    "    parallel(",
                    "      b: { throw new SimulatedFailureForRetry(); },",

                        // make sure this branch takes longer than a
                    "      a: { sh('sleep 3'); sh('touch b.done'); }",
                    "    )",
                    "    assert false;",
                    "  } catch (ParallelStepException e) {",
                    "    assert e.name=='b'",
                    "    assert e.cause instanceof SimulatedFailureForRetry",
                    "  }",
                    "}"
                )));

                startBuilding().get(15, SECONDS);
                assertBuildCompletedSuccessfully();
                assert jenkins().getWorkspaceFor(p).child("b.done").exists();

                buildTable();
                shouldHaveParallelStepsInTheOrder("b","a");
            }
        });
    }

    /**
     * Restarts in the middle of a parallel workflow.
     */
    @Test
    public void suspend() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                FilePath aa = jenkins().getRootPath().child("a");
                FilePath bb = jenkins().getRootPath().child("b");
                FilePath cc = jenkins().getRootPath().child("c");

                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                    "import "+SimulatedFailureForRetry.class.getName(),
                    "import "+ParallelStepException.class.getName(),

                    "with.node {",
                    "    parallel(",
                    "      a: { steps.watch(new File('"+aa+"')); sh('touch a.done'); },",
                    "      b: { steps.watch(new File('"+bb+"')); sh('touch b.done'); },",
                    "      c: { steps.watch(new File('"+cc+"')); sh('touch c.done'); },",
                    "    )",
                    "}"
                )));

                startBuilding();

                // let the workflow run until all parallel branches settle in the watch()
                for (int i=0; i<3; i++)
                    waitForWorkflowToSuspend();

                assert !e.isComplete() : b.getLog();
                assertEquals(3, watchDescriptor().getActiveWatches().size());
                assert e.getCurrentHeads().size()==3;
                assert b.isBuilding();

                buildTable();
                shouldHaveParallelStepsInTheOrder("a","b","c");
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);

                FilePath aa = jenkins().getRootPath().child("a");
                FilePath bb = jenkins().getRootPath().child("b");
                FilePath cc = jenkins().getRootPath().child("c");

                // make sure we are still running two heads
                assert e.getCurrentHeads().size()==3;
                assert b.isBuilding();

                // we let one branch go at a time
                for (FilePath f : asList(aa, bb)) {
                    f.touch(0);
                    watchDescriptor().watchUpdate();
                    waitForWorkflowToSuspend();

                    // until all execution joins into one, we retain all heads
                    assert e.getCurrentHeads().size() == 3;
                    assert b.isBuilding();
                }

                // when we let the last one go, it will now run till the completion
                cc.touch(0);
                watchDescriptor().watchUpdate();
                while (!e.isComplete())
                    waitForWorkflowToSuspend();

                // make sure all the three branches have executed to the end.
                for (String marker : asList("a", "b", "c")) {
                    assert jenkins().getWorkspaceFor(p).child(marker+".done").exists();
                }

                // check the shape of the graph
                buildTable();
                shouldHaveWatchSteps(ShellStep.DescriptorImpl.class, 3);
                shouldHaveWatchSteps(WatchYourStep.DescriptorImpl.class, 3);
                shouldHaveParallelStepsInTheOrder("a","b","c");
            }
        });
    }

    private void shouldHaveWatchSteps(Class<? extends StepDescriptor> d, int n) {
        int count=0;
        for (Row row : t.getRows()) {
            if (row.getNode() instanceof StepAtomNode) {
                StepAtomNode a = (StepAtomNode)row.getNode();
                if (a.getDescriptor().getClass()==d)
                    count++;
            }
        }
        assertEquals(n,count);
    }

    /**
     * Builds {@link FlowGraphTable}. Convenient for inspecting a shape of the flow nodes.
     */
    private FlowGraphTable buildTable() {
        t = new FlowGraphTable(e);
        t.build();
        return t;
    }
    private void shouldHaveParallelStepsInTheOrder(String... expected) {
        List<String> actual = new ArrayList<String>();

        for (Row row : t.getRows()) {
            ParallelLabelAction a = row.getNode().getAction(ParallelLabelAction.class);
            if (a!=null)
                actual.add(a.getBranchName());
        }

        assertEquals(Arrays.asList(expected),actual);
    }

    private Jenkins jenkins() {
        return story.j.jenkins;
    }

    private WatchYourStep.DescriptorImpl watchDescriptor() {
        return jenkins().getInjector().getInstance(WatchYourStep.DescriptorImpl.class);
    }

    private String join(String... args) {
        return StringUtils.join(args,"\n");
    }
}
