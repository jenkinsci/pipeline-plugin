/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.job

import org.jenkinsci.plugins.workflow.actions.LogAction
import org.jenkinsci.plugins.workflow.cps.AbstractCpsFlowTest
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution
import org.jenkinsci.plugins.workflow.graph.AtomNode
import org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep
import org.jenkinsci.plugins.workflow.support.actions.LogActionImpl
import hudson.model.Result
import org.junit.Before
import org.junit.Test

import javax.inject.Inject

/**
 * Test of {@link WorkflowJob} that doesn't involve Jenkins restarts.
 *
 * @author Kohsuke Kawaguchi
 */
public class WorkflowJobNonRestartingTest extends AbstractCpsFlowTest {
    @Inject
    DurableTaskStep.Checker checker;

    WorkflowJob p

    @Before
    public void setUp() {
        super.setUp()
        p = jenkins.jenkins.createProject(WorkflowJob.class, "demo")
    }

    @Test
    public void shellStep() {
        p.definition = new CpsFlowDefinition("""
            with.node {
              sh("echo hello world")
            }
        """)

        def f = p.scheduleBuild2(0)
        WorkflowRun b = f.startCondition.get()

        CpsFlowExecution e = b.executionPromise.get()
        e.waitForSuspension()

        Thread.sleep(1000)  // give a bit of time for shell script to complete
        checker.doRun()     // and let us notice that right away

        e.waitForSuspension()   // let the workflow run to the completion

        assert e.isComplete() : b.log
        assert b.result==Result.SUCCESS : b.log
        // currentHeads[0] is FlowEndNode, whose parent is BlockEndNode for "with.node",
        // whose parent is BlockEndNode for body invocation, whose parent is AtomNode
        AtomNode atom = e.currentHeads[0].parents[0].parents[0].parents[0]
        LogActionImpl la = atom.getAction(LogAction)
        assert la.logFile.text.contains("hello world")
    }

    /**
     * Obtains a node.
     */
    @Test
    public void nodeLease() {
        def s = jenkins.createSlave();
        s.computer.connect(false).get() // wait for the slave to fully get connected

        p.definition = new CpsFlowDefinition("""
            with.node {
                println 'Yo!'
            }
            println 'Out!'
        """)

        def f = p.scheduleBuild2(0)
        WorkflowRun b = f.startCondition.get()

        f.get(); // wait until completion

        assert b.result == Result.SUCCESS : b.log

        def log = b.logFile.text
        assert log.contains("Yo!")
        assert log.contains("Out!")
        assert log.indexOf("Yo!") < log.indexOf("Out!")
    }

    /**
     * Test the {@link org.jenkinsci.plugins.workflow.test.steps.RetryStep}.
     */
    @Test
    public void testRetry() {
        p.definition = new CpsFlowDefinition("""
            import org.jenkinsci.plugins.workflow.job.SimulatedFailureForRetry;

            int i = 0;
            retry(3) {
                println 'Trying!'
                if (i++<2)
                    throw new SimulatedFailureForRetry();
                println 'Done!'
            }
                println 'Over!'
        """)

        def f = p.scheduleBuild2(0)
        WorkflowRun b = f.startCondition.get()

        f.get(); // wait until completion

        def log = b.log
        println log;
        assert b.result == Result.SUCCESS

        def idx = 0;

        [
            "Trying!",
            "Trying!",
            "Trying!",
            "Done!",
            "Over!",
        ].each { msg ->
            idx = log.indexOf(msg,idx+1);
            assert idx!=-1 : msg+" not found";
        }

        [
            "Running: Retry the body up to N times : Start",
            "Running: Retry the body up to N times : start body : Start",
            "Running: Retry the body up to N times : start body : End",
            "Running: Retry the body up to N times : start body : Start",
            "Running: Retry the body up to N times : start body : End",
            "Running: Retry the body up to N times : start body : Start",
            "Running: Retry the body up to N times : start body : End",
            "Running: Retry the body up to N times : End",
        ].each { msg ->
            idx = log.indexOf(msg,idx+1);
            assert idx!=-1 : msg+" not found";
        }
    }
}
