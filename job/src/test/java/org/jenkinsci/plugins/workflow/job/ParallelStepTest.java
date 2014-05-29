package org.jenkinsci.plugins.workflow.job;

import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.ParallelStep;
import org.junit.Test;
import org.junit.runners.model.Statement;

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
                p.setDefinition(new CpsFlowDefinition(
                        "parallel( a: { println 1; }, b: { println 2; } )"
                ));

                startBuilding();
                waitForWorkflowToSuspend();
                assertBuildCompletedSuccessfully();

                System.out.println();
            }
        });
    }

    private Jenkins jenkins() {
        return story.j.jenkins;
    }
}
