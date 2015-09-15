package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Outcome;
import com.google.common.util.concurrent.ListenableFuture;
import hudson.Functions;
import hudson.model.Result;
import java.util.concurrent.Future;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Test;

public class CpsScriptTest extends AbstractCpsFlowTest {
    /**
     * Test the 'evaluate' method call.
     * The first test case.
     */
    @Test
    public void evaluate() throws Exception {
        CpsFlowDefinition flow = new CpsFlowDefinition("assert evaluate('1+2+3')==6");

        createExecution(flow);
        exec.start();
        exec.waitForSuspension();

        assertTrue(exec.isComplete());
        assertEquals(dumpError(), Result.SUCCESS, exec.getResult());
    }

    /**
     * The code getting evaluated must also get sandbox transformed.
     */
    @Test
    public void evaluateShallSandbox() throws Exception {
        CpsFlowDefinition flow = new CpsFlowDefinition("evaluate('Jenkins.getInstance()')", true);

        createExecution(flow);
        exec.start();
        exec.waitForSuspension();

        String msg = dumpError();

        // execution should have failed with error, pointing that Jenkins.getInstance() is not allowed from sandbox
        assertTrue(exec.isComplete());
        assertEquals(Result.FAILURE, exec.getResult());
        assertTrue(msg, msg.contains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance"));
    }

    /**
     * The code getting evaluated must also get CPS transformation.
     */
    @Ignore("TODO usually future == null, perhaps because CpsThread.resume is intended to be @CpsVmThreadOnly (so assumes that the promise is just set is not cleared by runNextChunk) yet we are calling it from the test thread; extremely dubious test design, should probably be using SemaphoreStep to be more realistic")
    @Test
    public void evaluateShallBeCpsTransformed() throws Exception {
        CpsFlowDefinition flow = new CpsFlowDefinition("evaluate('1+com.cloudbees.groovy.cps.Continuable.suspend(2+3)')");

        createExecution(flow);
        exec.start();
        exec.waitForSuspension();
        // TODO: can't we assert that the suspend() ended with value 5?

        // this should have paused at suspend, so we are going to resume it by having it return a value we control
        assertFalse(dumpError(), exec.isComplete());
        ListenableFuture<CpsThreadGroup> pp = exec.programPromise;
        assertNotNull(pp);
        Future<Object> future = pp.get().getThread(0).resume(new Outcome(7,null));
        assertNotNull(future);
        assertEquals(8, future.get());
        exec.waitForSuspension();

        assertTrue(dumpError(), exec.isComplete());
        assertEquals(dumpError(), Result.SUCCESS, exec.getResult());
    }

    /** Need to be careful that internal method names in {@link CpsScript} are not likely identifiers in user scripts. */
    @Test public void methodNameClash() throws Exception {
        CpsFlowDefinition flow = new CpsFlowDefinition("def build() {20}; def initialize() {10}; def env() {10}; def getShell() {2}; assert build() + initialize() + env() + shell == 42");
        createExecution(flow);
        exec.start();
        while (!exec.isComplete()) {
            exec.waitForSuspension();
        }
        assertEquals(dumpError(), Result.SUCCESS, exec.getResult());
    }

    /**
     * Picks up any errors recorded in {@link #exec}.
     */
    private String dumpError() {
        StringBuilder msg = new StringBuilder();

        FlowGraphWalker walker = new FlowGraphWalker(exec);
        for (FlowNode n : walker) {
            ErrorAction e = n.getAction(ErrorAction.class);
            if (e != null) {
                msg.append(Functions.printThrowable(e.getError()));
            }
        }
        return msg.toString();
    }
}
