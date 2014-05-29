package org.jenkinsci.plugins.workflow.job;

import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.ParallelStep;
import org.junit.Test;
import org.junit.runners.model.Statement;

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

                System.out.println();
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
