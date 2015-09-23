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
import hudson.model.BooleanParameterDefinition;
import hudson.model.BooleanParameterValue;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.tasks.ArtifactArchiver;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.apache.commons.httpclient.NameValuePair;
import org.jenkinsci.plugins.workflow.steps.CatchErrorStep;
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
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

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

    @Test public void coreStep2() throws Exception {
        assertRoundTrip(new CoreStep(new ArtifactArchiver("x.jar")), "step([$class: 'ArtifactArchiver', artifacts: 'x.jar'])");
    }

    @Test public void blockSteps() throws Exception {
        assertRoundTrip(new ExecutorStep(null), "node {\n    // some block\n}");
        assertRoundTrip(new ExecutorStep("linux"), "node('linux') {\n    // some block\n}");
        assertRoundTrip(new WorkspaceStep(null), "ws {\n    // some block\n}");
        assertRoundTrip(new WorkspaceStep("loc"), "ws('loc') {\n    // some block\n}");
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
        assertRoundTrip(step, "build job: 'downstream', parameters: [[$class: 'StringParameterValue', name: 'branch', value: 'default'], [$class: 'BooleanParameterValue', name: 'correct', value: true]]");
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
        assertGenerateSnippet("{'stapler-class':'" + EchoStep.class.getName() + "', 'message':'hello world'}", "echo 'hello world'", null);
    }

    @Issue("JENKINS-26093")
    @Test public void generateSnippetForBuildTrigger() throws Exception {
        MockFolder d1 = r.createFolder("d1");
        FreeStyleProject ds = d1.createProject(FreeStyleProject.class, "ds");
        MockFolder d2 = r.createFolder("d2");
        // Really this would be a WorkflowJob, but we cannot depend on that here, and it should not matter since we are just looking for Job:
        FreeStyleProject us = d2.createProject(FreeStyleProject.class, "us");
        ds.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("key", ""), new BooleanParameterDefinition("flag", false, "")));
        assertGenerateSnippet("{'stapler-class':'" + BuildTriggerStep.class.getName() + "', 'job':'../d1/ds', 'parameter': [{'name':'key', 'value':'stuff'}, {'name':'flag', 'value':true}]}", "build job: '../d1/ds', parameters: [[$class: 'StringParameterValue', name: 'key', value: 'stuff'], [$class: 'BooleanParameterValue', name: 'flag', value: true]]", us.getAbsoluteUrl() + "configure");
    }

    @Issue("JENKINS-29739")
    @Test public void generateSnippetForBuildTriggerSingle() throws Exception {
        FreeStyleProject ds = r.jenkins.createProject(FreeStyleProject.class, "ds1");
        FreeStyleProject us = r.jenkins.createProject(FreeStyleProject.class, "us1");
        ds.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("key", "")));
        assertGenerateSnippet("{'stapler-class':'" + BuildTriggerStep.class.getName() + "', 'job':'ds1', 'parameter': {'name':'key', 'value':'stuff'}}", "build job: 'ds1', parameters: [[$class: 'StringParameterValue', name: 'key', value: 'stuff']]", us.getAbsoluteUrl() + "configure");
    }

    @Test public void generateSnippetForBuildTriggerNone() throws Exception {
        FreeStyleProject ds = r.jenkins.createProject(FreeStyleProject.class, "ds0");
        FreeStyleProject us = r.jenkins.createProject(FreeStyleProject.class, "us0");
        assertGenerateSnippet("{'stapler-class':'" + BuildTriggerStep.class.getName() + "', 'job':'ds0'}", "build 'ds0'", us.getAbsoluteUrl() + "configure");
    }

    @Test public void generateSnippetAdvancedDeprecated() throws Exception {
        assertGenerateSnippet("{'stapler-class':'" + CatchErrorStep.class.getName() + "'}", "// " + Messages.Snippetizer_this_step_should_not_normally_be_used_in() + "\ncatchError {\n    // some block\n}", null);
    }

    private void assertGenerateSnippet(@Nonnull String json, @Nonnull String responseText, @CheckForNull String referer) throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();
        WebRequestSettings wrs = new WebRequestSettings(new URL(r.getURL(), Snippetizer.GENERATE_URL), HttpMethod.POST);
        if (referer != null) {
            wrs.setAdditionalHeader("Referer", referer);
        }
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new NameValuePair("json", json));
        // WebClient.addCrumb *replaces* rather than *adds*:
        params.add(new NameValuePair(r.jenkins.getCrumbIssuer().getDescriptor().getCrumbRequestField(), r.jenkins.getCrumbIssuer().getCrumb(null)));
        wrs.setRequestParameters(params);
        WebResponse response = wc.getPage(wrs).getWebResponse();
        assertEquals("text/plain", response.getContentType());
        assertEquals(responseText, response.getContentAsString().trim());
    }

}
