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

import hudson.FilePath;
import java.io.File;
import javax.inject.Inject;
import org.jenkinsci.plugins.workflow.test.steps.WatchYourStep;
import static org.junit.Assert.*;
import org.junit.Test;

public class CpsFlowDefinition2Test extends AbstractCpsFlowTest {

    @Inject WatchYourStep.DescriptorImpl watchDescriptor;

    /**
     * I should be able to have DSL call into async step and then bring it to the completion.
     */
    @Test public void suspendExecutionAndComeBack() throws Exception {
        File dir = tmp.newFolder();

        CpsFlowDefinition flow = new CpsFlowDefinition(
                "watch(new File('" + dir + "/marker'))\n" +
                "println 'Yo'");

        // get this going...
        createExecution(flow);
        exec.start();

        // it should stop at watch and suspend.
        exec.waitForSuspension();
        Thread.sleep(1000);  // wait a bit to really ensure workflow has suspended

        assertFalse("Expected the execution to be suspended but it has completed", exec.isComplete());
        assertEquals(1, watchDescriptor.getActiveWatches().size());

        exec = roundtripXStream(exec);    // poor man's simulation of Jenkins restart
        exec.onLoad();

        System.err.println("Touching the file");

        // now create the marker file to resume workflow execution
        new FilePath(new File(dir,"marker")).touch(0);
        watchDescriptor.watchUpdate();

        exec.waitForSuspension();
        assertTrue(exec.isComplete());
    }

}
