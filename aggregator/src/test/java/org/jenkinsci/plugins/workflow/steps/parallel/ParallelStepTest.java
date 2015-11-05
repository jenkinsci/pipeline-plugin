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
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStepException;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.EchoStep;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.durable_task.BatchScriptStep;
import org.jenkinsci.plugins.workflow.steps.durable_task.ShellStep;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable.Row;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Assert;
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
                    "      a: { sleep 3; writeFile text: '', file: 'a.done' }",
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
                assert jenkins().getWorkspaceFor(p).child("a.done").exists();

                buildTable();
                shouldHaveParallelStepsInTheOrder("b","a");
            }
        });
    }


    /**
     * Failure in a branch will cause the join to fail.
     */
    @Test @Issue("JENKINS-26034")
    public void failure_in_subflow_will_fail_fast() throws Exception {
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
                    "      a: { sleep 25; writeFile text: '', file: 'a.done' },",
                    "      failFast: true",
                    "    )",
                    "    assert false",
                    "  } catch (ParallelStepException e) {",
                    "    echo e.toString()",
                    "    assert e.name=='b'",
                    "    assert e.cause instanceof AbortException",
                    "  }",
                    "}"
                )));

                startBuilding().get();
                assertBuildCompletedSuccessfully();
                Assert.assertFalse("a should have aborted", jenkins().getWorkspaceFor(p).child("a.done").exists());

            }
        });
    }

    /**
     * FailFast should not kill branches if there is no failure.
     */
    @Test @Issue("JENKINS-26034")
    public void failFast_has_no_effect_on_success() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                    "import "+AbortException.class.getName(),
                    "import "+ParallelStepException.class.getName(),

                    "node {",
                    "    parallel(",
                    "      a: { echo 'hello from a';sleep 1;echo 'goodbye from a' },",
                    "      b: { echo 'hello from b';sleep 1;echo 'goodbye from b' },",
                    "      c: { echo 'hello from c';sleep 1;echo 'goodbye from c' },",
                    // make sure this branch is quicker than the others.
                    "      d: { echo 'hello from d' },",
                    "      failFast: true",
                    "    )",
                    "}"
                )));

                startBuilding().get();
                assertBuildCompletedSuccessfully();
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
                    "def touch(f) { writeFile text: '', file: f }",
                    "node {",
                    "  parallel(aa: {touch($/" + aa + "/$)}, bb: {touch($/" + bb + "/$)})",
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
                        "  echo msg",
                        "}",
                        "node {",
                        "  ws {",
                        "    echo 'start'",
                        "    parallel(one: {",
                        "      notify('one')",
                        "    }, two: {",
                        "      notify('two')",
                        "    })",
                        "    echo 'end'",
                        "  }",
                        "}"
                )));

                startBuilding().get();
                assertBuildCompletedSuccessfully();
                story.j.assertLogContains("one", b);
                story.j.assertLogContains("two", b);
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
                        StepDescriptor descriptor = ((StepAtomNode)r.getNode()).getDescriptor();
                        if (descriptor instanceof ShellStep.DescriptorImpl || descriptor instanceof BatchScriptStep.DescriptorImpl) {
                            shell++;
                        }
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
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                    "node {",
                    "    parallel(",
                    "      a: { semaphore 'suspendA'; echo 'A done' },",
                    "      b: { semaphore 'suspendB'; echo 'B done' },",
                    "      c: { semaphore 'suspendC'; echo 'C done' },",
                    "    )",
                    "}"
                )));

                startBuilding();

                // let the workflow run until all parallel branches settle
                SemaphoreStep.waitForStart("suspendA/1", b);
                SemaphoreStep.waitForStart("suspendB/1", b);
                SemaphoreStep.waitForStart("suspendC/1", b);

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

                // make sure we are still running two heads
                assert e.getCurrentHeads().size()==3;
                assert b.isBuilding();

                // we let one branch go at a time
                for (String branch : asList("A", "B")) {
                    SemaphoreStep.success("suspend" + branch + "/1", null);
                    waitForWorkflowToSuspend();

                    // until all execution joins into one, we retain all heads
                    assert e.getCurrentHeads().size() == 3;
                    assert b.isBuilding();
                }

                // when we let the last one go, it will now run till the completion
                SemaphoreStep.success("suspendC/1", null);
                while (!e.isComplete())
                    waitForWorkflowToSuspend();

                // make sure all the three branches have executed to the end.
                for (String branch : asList("A", "B", "C")) {
                    story.j.assertLogContains(branch + " done", b);
                }

                // check the shape of the graph
                buildTable();
                shouldHaveSteps(SemaphoreStep.DescriptorImpl.class, 3);
                shouldHaveSteps(EchoStep.DescriptorImpl.class, 3);
                shouldHaveParallelStepsInTheOrder("a","b","c");
            }
        });
    }

    private void shouldHaveSteps(Class<? extends StepDescriptor> d, int n) {
        int count=0;
        for (Row row : t.getRows()) {
            if (row.getNode() instanceof StepAtomNode) {
                StepAtomNode a = (StepAtomNode)row.getNode();
                if (a.getDescriptor().getClass()==d)
                    count++;
            }
        }
        assertEquals(d.getName(), n, count);
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
            ThreadNameAction a = row.getNode().getAction(ThreadNameAction.class);
            if (a!=null)
                actual.add(a.getThreadName());
        }

        assertEquals(Arrays.asList(expected),actual);
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

    @Test
    @Issue("JENKINS-26122")
    public void parallelBranchLabels() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                        "node {\n" +
                                "    parallel( \n" +
                                "        a: { \n" +
                                "            echo('echo a');\n" +
                                "            echo('echo a');\n" +
                                "        }, \n" +
                                "        b: { \n" +
                                "            echo('echo b'); \n" +
                                "            echo('echo b'); \n" +
                                "        }\n" +
                                "    )\n" +
                                "}\n"
                )));

                startBuilding().get();
                assertBuildCompletedSuccessfully();

                // Check that the individual labeled lines are as expected
                //System.out.println(b.getLog());
                List<String> logLines = b.getLog(50);
                assertGoodLabeledLogs(logLines);

                // Check that the logs are printed in the right sequence e.g. that a
                // "[a] Running: Print Message" is followed by a "[a] echo a"
                assertGoodSequence("a", logLines);
                assertGoodSequence("b", logLines);
            }
            private void assertGoodLabeledLogs(List<String> logLines) {
                for (int i = 0; i < logLines.size(); i++) {
                    String logLine = logLines.get(i);
                    if (logLine.startsWith("[a] ")) {
                        assertGoodLabeledLog("a", logLine);
                    } else if (logLine.startsWith("[b] ")) {
                        assertGoodLabeledLog("b", logLine);
                    }
                }
            }
            private void assertGoodLabeledLog(String label, String logLine) {
                List<String> possibleLogLines = Arrays.asList(
                        String.format("[%s] Running: Parallel branch: %s", label, label),
                        String.format("[%s] Running: Print Message", label),
                        String.format("[%s] echo %s", label, label)
                );
                boolean contains = possibleLogLines.contains(logLine);
                assertTrue(contains);
            }
            private void assertGoodSequence(String label, List<String> logLines) {
                String running = String.format("[%s] Running: Print Message", label);
                String echo = String.format("[%s] echo %s", label, label);

                for (int i = 0; i < logLines.size() - 1; i++) { // skip the last log line in this loop
                    if (logLines.get(i).equals(running)) {
                        assertEquals(echo, logLines.get(i + 1));
                    }
                }
            }
        });
    }
}
