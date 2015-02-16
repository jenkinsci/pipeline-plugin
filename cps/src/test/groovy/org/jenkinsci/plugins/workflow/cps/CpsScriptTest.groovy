package org.jenkinsci.plugins.workflow.cps

import com.cloudbees.groovy.cps.Outcome
import hudson.Functions
import hudson.model.Result
import org.jenkinsci.plugins.workflow.actions.ErrorAction
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker
import org.junit.Test
import org.jvnet.hudson.test.RandomlyFails

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class CpsScriptTest extends AbstractCpsFlowTest {
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
        assert exec.result==Result.SUCCESS : dumpError();
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

    /**
     * The code getting evaluated must also get CPS transformation.
     */
    @RandomlyFails("TODO future != null")
    @Test
    void evaluateShallBeCpsTransformed() {
        def flow = new CpsFlowDefinition("""
evaluate('1+com.cloudbees.groovy.cps.Continuable.suspend(2+3)')
""")

        createExecution(flow)
        exec.start()
        exec.waitForSuspension()
        // TODO: can't we assert that the suspend() ended with value 5?

        // this should have paused at suspend, so we are going to resume it by having it return a value we control
        assert !exec.isComplete() : dumpError()
        def pp = exec.programPromise
        assert pp != null
        def future = pp.get().getThread(0).resume(new Outcome(7,null))
        assert future != null
        assert future.get()==8
        exec.waitForSuspension()

        assert exec.isComplete() : dumpError();
        assert exec.result==Result.SUCCESS : dumpError();
    }

    /**
     * Picks up any errors recorded in {@link #exec}.
     */
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
