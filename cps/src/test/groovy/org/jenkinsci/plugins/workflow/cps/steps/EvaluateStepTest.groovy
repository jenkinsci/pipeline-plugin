package org.jenkinsci.plugins.workflow.cps.steps

import hudson.Functions
import hudson.model.Result
import org.jenkinsci.plugins.workflow.actions.ErrorAction
import org.jenkinsci.plugins.workflow.cps.AbstractCpsFlowTest
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker
import org.junit.Test

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class EvaluateStepTest extends AbstractCpsFlowTest {
    /**
     * Test the 'evaluate' method call.
     * The first test case.
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

    /**
     * The code getting evaluated must also get sandbox transformed.
     */
    @Test
    void evaluateShallSandbox() {
        def flow = new CpsFlowDefinition("""
evaluate('Jenkins.getInstance()')
""",true)

        createExecution(flow)
        exec.start()
        exec.waitForSuspension()

        def msg = dumpError()

        // execution should have failed with error, pointing that Jenkins.getInstance() is not allowed from sandbox
        assert exec.isComplete()
        assert exec.result==Result.FAILURE;
        assert msg.contains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance") : msg
    }

    private String dumpError() {
        def msg = ""

        def walker = new FlowGraphWalker(exec);
        def n;
        while ((n = walker.next()) != null) {
            def e = n.getAction(ErrorAction.class)
            if (e != null)
                msg += Functions.printThrowable(e.error)
        }
        return msg;
    }
}
