package org.jenkinsci.plugins.workflow.job;

import org.jenkinsci.plugins.workflow.cps.AbstractCpsFlowTest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.junit.Test;
import org.junit.runners.model.Statement;

/**
 * @author Kohsuke Kawaguchi
 */
public class PersistenceFailureTest extends SingleJobTestBase {
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
}
