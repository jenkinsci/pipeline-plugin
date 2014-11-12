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

import groovy.lang.GroovyObjectSupport;
import groovy.lang.GroovyShell;
import hudson.model.BooleanParameterValue;
import hudson.model.StringParameterValue;
import hudson.tasks.ArtifactArchiver;
import java.util.Arrays;
import org.jenkinsci.plugins.workflow.steps.CoreStep;
import org.jenkinsci.plugins.workflow.steps.EchoStep;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;
import org.jenkinsci.plugins.workflow.support.steps.WorkspaceStep;
import org.jenkinsci.plugins.workflow.support.steps.build.BuildTriggerStep;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.jvnet.hudson.test.JenkinsRule;

public class SnippetizerTest {

    @ClassRule public static JenkinsRule r = new JenkinsRule();
    
    @Test public void basics() throws Exception {
        assertRoundTrip(new EchoStep("hello world"), "echo 'hello world'");
        StageStep s = new StageStep("Build");
        assertRoundTrip(s, "stage 'Build'");
        s.concurrency = 1;
        assertRoundTrip(s, "stage concurrency: 1, name: 'Build'");
    }

    @Test public void coreStep() throws Exception {
        ArtifactArchiver aa = new ArtifactArchiver("x.jar");
        aa.setAllowEmptyArchive(true);
        assertRoundTrip(new CoreStep(aa), "step([$class: 'ArtifactArchiver', allowEmptyArchive: true, artifacts: 'x.jar', defaultExcludes: true, excludes: '', fingerprint: false, onlyIfSuccessful: false])");
    }

    @Test public void blockSteps() throws Exception {
        assertRoundTrip(new ExecutorStep(null), "node {\n    // some block\n}");
        assertRoundTrip(new ExecutorStep("linux"), "node('linux') {\n    // some block\n}");
        assertRoundTrip(new WorkspaceStep(), "ws {\n    // some block\n}");
    }

    @Test public void escapes() throws Exception {
        assertRoundTrip(new EchoStep("Bob's message \\/ here"), "echo 'Bob\\'s message \\\\/ here'");
    }

    @Test public void multilineStrings() throws Exception {
        assertRoundTrip(new EchoStep("echo hello\necho 1/2 way\necho goodbye"), "echo '''echo hello\necho 1/2 way\necho goodbye'''");
    }

    @Test public void javaObjects() throws Exception {
        BuildTriggerStep step = new BuildTriggerStep("downstream");
        assertRoundTrip(step, "build 'downstream'");
        step.setParameters(Arrays.asList(new StringParameterValue("branch", "default"), new BooleanParameterValue("correct", true)));
        /* TODO figure out how to add support for ParameterValue without those having Descriptor’s yet:
        assertRoundTrip(step, "build job: 'downstream', parameters: [[$class: 'StringParameterValue', name: 'branch', value: 'default'], [$class: 'BooleanParameterValue', name: 'correct', value: true]]");
        */
        assertRender("null", null);
    }

    private static void assertRoundTrip(Step step, String expected) throws Exception {
        assertEquals(expected, Snippetizer.object2Groovy(step));
        GroovyShell shell = new GroovyShell(r.jenkins.getPluginManager().uberClassLoader);
        final StepDescriptor desc = step.getDescriptor();
        shell.setVariable("steps", new GroovyObjectSupport() {
            @Override public Object invokeMethod(String name, Object args) {
                if (name.equals(desc.getFunctionName())) {
                    try {
                        return desc.newInstance(DSL.parseArgs(desc, args).namedArgs);
                    } catch (RuntimeException x) {
                        throw x;
                    } catch (Exception x) {
                        throw new RuntimeException(x);
                    }
                } else {
                    return super.invokeMethod(name, args);
                }
            }
        });
        Step actual = (Step) shell.evaluate("steps." + expected);
        r.assertEqualDataBoundBeans(step, actual);
    }

    private static void assertRender(String expected, Object o) {
        StringBuilder b = new StringBuilder();
        Snippetizer.render(b, o);
        assertEquals(expected, b.toString());
    }

}
