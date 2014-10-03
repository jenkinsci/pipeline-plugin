package org.jenkinsci.plugins.workflow.cps.steps

import hudson.model.Result
import org.jenkinsci.plugins.workflow.cps.AbstractCpsFlowTest
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.junit.Test

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class EvaluateStepTest extends AbstractCpsFlowTest {
    /**
     * Test the 'evaluate' method call
     */
    @Test
    void evaluate() {
        def flow = new CpsFlowDefinition("""
assert evaluate('1+2+3')==6
""")

        createExecution(flow)
        exec.start()
        exec.waitForSuspension()

        assert exec.isComplete()
        assert exec.result==Result.SUCCESS;
    }
}
