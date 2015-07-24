/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

package org.jenkinsci.plugins.workflow.cps;

import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.*;
import org.junit.Test;

public class CpsFlowDefinition2Test extends AbstractCpsFlowTest {

    /**
     * I should be able to have DSL call into async step and then bring it to the completion.
     */
    @Test public void suspendExecutionAndComeBack() throws Exception {
        CpsFlowDefinition flow = new CpsFlowDefinition(
                "semaphore 'watch'\n" +
                "println 'Yo'");

        // get this going...
        createExecution(flow);
        exec.start();

        SemaphoreStep.waitForStart("watch/1", null);

        assertFalse("Expected the execution to be suspended but it has completed", exec.isComplete());

        FlowExecutionOwner owner = exec.getOwner();
        exec = roundtripXStream(exec);    // poor man's simulation of Jenkins restart
        exec.onLoad(owner);

        // now resume workflow execution
        SemaphoreStep.success("watch/1", null);

        exec.waitForSuspension();
        assertTrue(exec.isComplete());
    }

}
