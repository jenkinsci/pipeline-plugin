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

package org.jenkinsci.plugins.workflow.cps

import org.jenkinsci.plugins.workflow.test.steps.WatchYourStep
import org.junit.Ignore
import hudson.FilePath
import org.junit.Test

import javax.inject.Inject

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class CpsFlowDefinitionTest extends AbstractCpsFlowTest {

    /**
     * CpsFlowDefinition's simplest possible test.
     */
    @Test
    public void simplestPossibleTest() {
        def flow = new CpsFlowDefinition("""
def sqrt(int x) {
    return Math.sqrt(x);
}

for (int i=0; i<10; i++)
    sqrt(i);

""")

        createExecution(flow)
        exec.start()
        exec.waitForSuspension()

        assert exec.isComplete()
    }

    /**
     * I should be able to have DSL call into async step and then bring it to the completion.
     */
    @Test
    @Ignore("TODO cannot work now since WatchYourStep relies on StepExecution.applyAll which looks for StepExecutionIterator, which would need JenkinsRule")
    void suspendExecutionAndComeBack() {
        def dir = tmp.newFolder()

        def flow = new CpsFlowDefinition("""
            watch(new File("${dir}/marker"))
            println 'Yo'
        """)

        // get this going...
        createExecution(flow)
        exec.start()

        // it should stop at watch and suspend.
        exec.waitForSuspension()
        Thread.sleep(1000)  // wait a bit to really ensure workflow has suspended

        assert !exec.isComplete() : "Expected the execution to be suspended but it has completed";
        assert WatchYourStep.activeCount == 1

        exec = roundtripXStream(exec);    // poor man's simulation of Jenkins restart
        exec.onLoad()

        println "Touching the file";

        // now create the marker file to resume workflow execution
        new FilePath(new File(dir,"marker")).touch(0);
        WatchYourStep.update();

        exec.waitForSuspension();
        assert exec.isComplete()
    }

    @Test
    void exceptionInWorkflowShouldBreakFlowExecution() throws Exception {
        def flow = new CpsFlowDefinition("""
            throw new Throwable('This is a fire drill, not a real fire');
        """)

        // get this going...
        createExecution(flow)
        exec.start()

        // it should stop at watch and suspend.
        exec.waitForSuspension()
        assert exec.isComplete()
        def t = exec.getCauseOfFailure()
        assert t.getClass()==Throwable.class
        assert t.message=="This is a fire drill, not a real fire";
    }
}
