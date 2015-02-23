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

import hudson.FilePath
import org.junit.Test
import static org.junit.Assert.*;

import javax.inject.Inject
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.WebResponse;
import java.net.URL;
import org.apache.commons.httpclient.NameValuePair;
import org.jvnet.hudson.test.JenkinsRule;

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
