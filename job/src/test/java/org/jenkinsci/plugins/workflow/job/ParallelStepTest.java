package org.jenkinsci.plugins.workflow.job;

import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.ParallelStep;
import org.jenkinsci.plugins.workflow.cps.ParallelStepException;
import org.junit.Test;
import org.junit.runners.model.Statement;

import java.io.File;

import static java.util.concurrent.TimeUnit.*;

/**
 * Tests for {@link ParallelStep}.
 *
 * @author Kohsuke Kawaguchi
 */
public class ParallelStepTest extends SingleJobTestBase {
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

                startBuilding().get(15, SECONDS);
                assertBuildCompletedSuccessfully();
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
                    "      a: { throw new SimulatedFailureForRetry(); },",

                        // make sure this branch takes longer than a
                    "      b: { sh('sleep 3'); sh('touch b.done'); }",
                    "    )",
                    "    assert false;",
                    "  } catch (ParallelStepException e) {",
                    "    assert e.name=='a'",
                    "    assert e.cause instanceof SimulatedFailureForRetry",
                    "  }",
                    "}"
                )));

                startBuilding().get(15, SECONDS);
                assertBuildCompletedSuccessfully();
                assert jenkins().getWorkspaceFor(p).child("b.done").exists();
            }
        });
    }


    private Jenkins jenkins() {
        return story.j.jenkins;
    }

    private String join(String... args) {
        return StringUtils.join(args,"\n");
    }
}
