/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class SnippetizerTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    
    @Test public void basics() {
        assertProcess("{'stapler-class':'org.jenkinsci.plugins.workflow.steps.EchoStep', 'value':'hello world'}", "echo 'hello world'");
    }

    // TODO StageStep probably needs to not override newInstance, should have @DataBoundSetter for concurrency as Integer (though produces "concurrency":"")
    // TODO test block arguments like node {} and node('label') {}
    // TODO CoreStep; perhaps will require some kind of API in StepDescriptor to show how to handle $class
    // TODO BuildTriggerStep incl. parameters
    // TODO escaping '
    // TODO multiline text should produce / or ''' strings
    // TODO multiple args should be separated by ,

    private static void assertProcess(String json, String groovy) {
        assertEquals(groovy, Snippetizer.json2Groovy(json.replace('\'', '"')));
    }

}
