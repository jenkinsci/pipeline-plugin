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

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.WebResponse;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.GroovyShell;
import hudson.model.BooleanParameterValue;
import hudson.model.StringParameterValue;
import hudson.tasks.ArtifactArchiver;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.httpclient.NameValuePair;
import org.jenkinsci.plugins.workflow.steps.CoreStep;
import org.jenkinsci.plugins.workflow.steps.EchoStep;
import org.jenkinsci.plugins.workflow.steps.PwdStep;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;
import org.jenkinsci.plugins.workflow.support.steps.WorkspaceStep;
import org.jenkinsci.plugins.workflow.support.steps.build.BuildTriggerStep;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStep;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.Issue;
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

    @Email("https://groups.google.com/forum/#!topicsearchin/jenkinsci-users/workflow/jenkinsci-users/DJ15tkEQPw0")
    @Test public void noArgStep() throws Exception {
        assertRoundTrip(new PwdStep(), "pwd()");
    }

    @Test public void coreStep() throws Exception {
        ArtifactArchiver aa = new ArtifactArchiver("x.jar");
        aa.setAllowEmptyArchive(true);
        assertRoundTrip(new CoreStep(aa), "step([$class: 'ArtifactArchiver', allowEmptyArchive: true, artifacts: 'x.jar'])");
    }

    @Ignore("TODO until 1.601+ expected:<step[([$class: 'ArtifactArchiver', artifacts: 'x.jar'])]> but was:<step[ <object of type hudson.tasks.ArtifactArchiver>]>")
    @Test public void coreStep2() throws Exception {
        assertRoundTrip(new CoreStep(new ArtifactArchiver("x.jar")), "step([$class: 'ArtifactArchiver', artifacts: 'x.jar'])");
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

    @Test public void buildTriggerStep() throws Exception {
        BuildTriggerStep step = new BuildTriggerStep("downstream");
        assertRoundTrip(step, "build 'downstream'");
        step.setParameters(Arrays.asList(new StringParameterValue("branch", "default"), new BooleanParameterValue("correct", true)));
        /* TODO figure out how to add support for ParameterValue without those having Descriptor’s yet
                currently instantiate works but uninstantiate does not offer a FQN
                (which does not matter in this case since BuildTriggerStep/config.jelly does not offer to bind parameters anyway)
        assertRoundTrip(step, "build job: 'downstream', parameters: [[$class: 'hudson.model.StringParameterValue', name: 'branch', value: 'default'], [$class: 'hudson.model.BooleanParameterValue', name: 'correct', value: true]]");
        */
    }

    @Issue("JENKINS-25779")
    @Test public void defaultValues() throws Exception {
        assertRoundTrip(new InputStep("Ready?"), "input 'Ready?'");
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

    @Test public void generateSnippet() throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();
        WebRequestSettings wrs = new WebRequestSettings(new URL(r.getURL(), Snippetizer.GENERATE_URL), HttpMethod.POST);
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new NameValuePair("json", "{'stapler-class':'org.jenkinsci.plugins.workflow.steps.EchoStep', 'message':'hello world'}"));
        // WebClient.addCrumb *replaces* rather than *adds*:
        params.add(new NameValuePair(r.jenkins.getCrumbIssuer().getDescriptor().getCrumbRequestField(), r.jenkins.getCrumbIssuer().getCrumb(null)));
        wrs.setRequestParameters(params);
        WebResponse response = wc.getPage(wrs).getWebResponse();
        assertEquals("text/plain", response.getContentType());
        assertEquals("echo 'hello world'\n", response.getContentAsString());
    }

}
