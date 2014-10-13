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

import hudson.tasks.ArtifactArchiver;
import org.jenkinsci.plugins.workflow.steps.CoreStep;
import org.jenkinsci.plugins.workflow.steps.EchoStep;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;
import org.jenkinsci.plugins.workflow.support.steps.WorkspaceStep;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.jvnet.hudson.test.JenkinsRule;

public class SnippetizerTest {

    @ClassRule public static JenkinsRule r = new JenkinsRule();
    
    @Test public void basics() {
        assertEquals("echo 'hello world'", Snippetizer.object2Groovy(new EchoStep("hello world")));
    }

    @Test public void coreStep() {
        ArtifactArchiver aa = new ArtifactArchiver("x.jar");
        aa.setAllowEmptyArchive(true);
        assertEquals("step $class: 'hudson.tasks.ArtifactArchiver', allowEmptyArchive: true, artifacts: 'x.jar', defaultExcludes: true, excludes: '', fingerprint: false, onlyIfSuccessful: false", Snippetizer.object2Groovy(new CoreStep(aa)));
    }

    @Test public void blockSteps() {
        assertEquals("node {\n    // some block\n}", Snippetizer.object2Groovy(new ExecutorStep(null)));
        assertEquals("node('linux') {\n    // some block\n}", Snippetizer.object2Groovy(new ExecutorStep("linux")));
        assertEquals("ws {\n    // some block\n}", Snippetizer.object2Groovy(new WorkspaceStep()));
    }

    @Test public void escapes() {
        assertEquals("echo 'Bob\\'s message \\\\/ here'", Snippetizer.object2Groovy(new EchoStep("Bob's message \\/ here")));
    }

    @Test public void multilineStrings() {
        assertEquals("echo /echo hello\necho 1\\/2 way\necho goodbye/", Snippetizer.object2Groovy(new EchoStep("echo hello\necho 1/2 way\necho goodbye")));
    }

    // TODO StageStep probably needs to not override newInstance, should have @DataBoundSetter for concurrency as Integer (though produces "concurrency":"")
    // TODO BuildTriggerStep incl. parameters

}
