package org.jenkinsci.plugins.workflow.steps.parallel;

import hudson.AbortException;
import hudson.model.Result;
import hudson.FilePath;
import java.util.ArrayList;
import java.util.Arrays;
import static java.util.Arrays.*;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.SingleJobTestBase;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep.ParallelLabelAction;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStepException;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.durable_task.ShellStep;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable.Row;
import org.jenkinsci.plugins.workflow.test.steps.WatchYourStep;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;

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
                    "node {",
                    "  x = parallel( a: { echo('echo a'); return 1; }, b: { echo('echo b'); return 2; } )",
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
                    "import "+AbortException.class.getName(),
                    "import "+ParallelStepException.class.getName(),

                    "node {",
                    "  try {",
                    "    parallel(",
                    "      b: { error 'died' },",

                        // make sure this branch takes longer than a
                    "      a: { sh('sleep 3'); sh('touch b.done'); }",
                    "    )",
                    "    assert false;",
                    "  } catch (ParallelStepException e) {",
                    "    assert e.name=='b'",
                    "    assert e.cause instanceof AbortException",
                    "  }",
                    "}"
                )));

                startBuilding().get();
                assertBuildCompletedSuccessfully();
                assert jenkins().getWorkspaceFor(p).child("b.done").exists();

                buildTable();
                shouldHaveParallelStepsInTheOrder("b","a");
            }
        });
    }

    /**
     * Nameless closures.
     */
    @Ignore("TODO CpsBuiltinSteps.parallel(Closure[]) used to handle this, but there is no replacement")
    @Test
    public void nameslessBranches() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                FilePath aa = jenkins().getRootPath().child("a");
                FilePath bb = jenkins().getRootPath().child("b");

                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                    "node {",
                    "  parallel( { sh('touch "+aa+"'); }, { sh('touch "+bb+"'); } )",
                    "}"
                )));

                startBuilding().get();
                assertBuildCompletedSuccessfully();

                assertTrue(aa.exists());
                assertTrue(bb.exists());
            }
        });
    }

    @Test
    public void localMethodCallWithinBranch() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                FilePath aa = jenkins().getRootPath().child("a");
                FilePath bb = jenkins().getRootPath().child("b");

                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                    "def touch(f) { sh 'touch '+f }",
                    "node {",
                    "  parallel(aa: {touch '" + aa + "'}, bb: {touch '" + bb + "'})",
                    "}"
                )));

                startBuilding().get();
                assertBuildCompletedSuccessfully();

                assertTrue(aa.exists());
                assertTrue(bb.exists());
            }
        });
    }

    @Test
    public void localMethodCallWithinBranch2() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                        "def notify(msg) {",
                        "  sh \"echo ${msg}\"",
                        "}",
                        "node {",
                        "  ws {",
                        "    sh 'echo start'",
                        "    parallel(one: {",
                        "      notify('one')",
                        "    }, two: {",
                        "      notify('two')",
                        "    })",
                        "    sh 'echo end'",
                        "  }",
                        "}"
                )));

                startBuilding().get();
                assertBuildCompletedSuccessfully();
            }
        });
    }

    @Test
    public void localMethodCallWithinLotsOfBranches() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                        IOUtils.toString(getClass().getResource("localMethodCallWithinLotsOfBranches.groovy"))));

                startBuilding().get();
                assertBuildCompletedSuccessfully();

                // count number of shell steps
                FlowGraphTable t = buildTable();
                int shell=0;
                for (Row r : t.getRows()) {
                    if (r.getNode() instanceof StepAtomNode) {
                        if (((StepAtomNode)r.getNode()).getDescriptor() instanceof ShellStep.DescriptorImpl)
                        shell++;
                    }
                }
                assertEquals(128*3,shell);
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
                    "import "+ParallelStepException.class.getName(),

                    "node {",
                    "    parallel(",
                    "      a: { watch(new File('"+aa+"')); sh('touch a.done'); },",
                    "      b: { watch(new File('"+bb+"')); sh('touch b.done'); },",
                    "      c: { watch(new File('"+cc+"')); sh('touch c.done'); },",
                    "    )",
                    "}"
                )));

                startBuilding();

                // let the workflow run until all parallel branches settle in the watch()
                while (watchDescriptor().getActiveWatches().size()<3 && !e.isComplete())
                    waitForWorkflowToSuspend();

                System.out.println(b.getLog());
                assert !e.isComplete();
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

    private WatchYourStep.DescriptorImpl watchDescriptor() {
        return jenkins().getInjector().getInstance(WatchYourStep.DescriptorImpl.class);
    }

    /**
     * Parallel branches become invisible once completed until the whole parallel step is completed.
     */
    @Test @Issue("JENKINS-26074")
    public void invisibleParallelBranch() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                    "    parallel(\n" +
                    "      'abc' : {\n" +
                    "        noSuchFunctionExists(); \n"+
                    "      }\n" +
                    "      ,\n" +
                    "      'waitForever' : {\n" +
                    "        input message: 'This is just to prove a point' \n" +
                    "      }\n" +
                    "      ,\n" +
                    "      'someSimpleError' : {\n" +
                    "        noSuchFunctionExists(); \n"+
                    "      }\n" +
                    "    )\n"
                )));

                startBuilding();

                // wait for workflow to progress far enough to the point that it has finished  failing two branches
                // and pause on one
                for (int i=0; i<10; i++)
                    waitForWorkflowToSuspend();

                InputAction a = b.getAction(InputAction.class);
                assertNotNull("Expected to pause on input",a);

                assertEquals("Expecting 3 heads for 3 branches", 3,e.getCurrentHeads().size());

                a.getExecutions().get(0).proceed(null);
                waitForWorkflowToComplete();

                story.j.assertBuildStatus(Result.FAILURE, b);

                // make sure the table builds OK
                buildTable();
            }
        });
    }
}
