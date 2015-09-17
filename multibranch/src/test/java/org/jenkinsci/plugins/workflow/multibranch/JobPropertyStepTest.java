/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.multibranch;

import hudson.model.BooleanParameterDefinition;
import hudson.model.JobProperty;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import java.util.Collections;
import java.util.List;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.plugins.git.GitSCMSource;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectTest.findBranchProject;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.steps.scm.GitSampleRepoRule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class JobPropertyStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Issue("JENKINS-30519")
    @Test public void configRoundTrip() throws Exception {
        StepConfigTester tester = new StepConfigTester(r);
        // TODO fails (returns null)
        assertEquals(Collections.emptyList(), tester.configRoundTrip(new JobPropertyStep(Collections.<JobProperty<?>>emptyList())).getProperties());
        List<JobProperty<?>> properties = tester.configRoundTrip(new JobPropertyStep(Collections.<JobProperty<?>>singletonList(new ParametersDefinitionProperty(new BooleanParameterDefinition("flag", true, null))))).getProperties();
        assertEquals(1, properties.size());
        assertEquals(ParametersDefinitionProperty.class, properties.get(0));
        ParametersDefinitionProperty pdp = (ParametersDefinitionProperty) properties.get(0);
        assertEquals(1, pdp.getParameterDefinitions().size());
        assertEquals(BooleanParameterDefinition.class, pdp.getParameterDefinitions().get(0).getClass());
        BooleanParameterDefinition bpd = (BooleanParameterDefinition) pdp.getParameterDefinitions().get(0);
        assertEquals("flag", bpd.getName());
        assertTrue(bpd.isDefaultValue());
        // TODO Snippetizer fails with: NoStaplerConstructorException: There's no @DataBoundConstructor on any constructor of class hudson.model.ParametersDefinitionProperty
        // TODO also JENKINS-29711 means it seems to omit the required ()
    }

    @Issue("JENKINS-30206")
    @Test public void useParameter() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile",
                "properties([[$class: 'ParametersDefinitionProperty', parameterDefinitions: [[$class: 'StringParameterDefinition', name: 'myparam', defaultValue: 'default value']]]])\n" +
                "echo \"received ${myparam}\"");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        WorkflowJob p = findBranchProject(mp, "master");
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        // TODO fails due to JENKINS-28510
        r.assertLogContains("received default value", b1);
        WorkflowRun b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("myparam", "special value"))));
        assertEquals(2, b2.getNumber());
        r.assertLogContains("received special value", b2);
    }

}
