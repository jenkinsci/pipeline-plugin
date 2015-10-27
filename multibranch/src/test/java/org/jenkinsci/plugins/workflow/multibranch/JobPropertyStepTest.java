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
import hudson.tasks.LogRotator;
import java.util.Collections;
import java.util.List;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.model.BuildDiscarder;
import jenkins.plugins.git.GitSCMSource;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.properties.BuildDiscarderProperty;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.steps.scm.GitSampleRepoRule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import static org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject;
import org.junit.Ignore;

@Issue("JENKINS-30519")
public class JobPropertyStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Ignore("TODO fails with a JS TypeError, which goes away on new cores, maybe due to new HtmlUnit (1.627+); and needs https://github.com/jenkinsci/branch-api-plugin/pull/9")
    @SuppressWarnings("rawtypes")
    @Test public void configRoundTripParameters() throws Exception {
        StepConfigTester tester = new StepConfigTester(r);
        assertEquals(Collections.emptyList(), tester.configRoundTrip(new JobPropertyStep(Collections.<JobProperty>emptyList())).getProperties());
        List<JobProperty> properties = tester.configRoundTrip(new JobPropertyStep(Collections.<JobProperty>singletonList(new ParametersDefinitionProperty(new BooleanParameterDefinition("flag", true, null))))).getProperties();
        assertEquals(1, properties.size());
        assertEquals(ParametersDefinitionProperty.class, properties.get(0).getClass());
        ParametersDefinitionProperty pdp = (ParametersDefinitionProperty) properties.get(0);
        assertEquals(1, pdp.getParameterDefinitions().size());
        assertEquals(BooleanParameterDefinition.class, pdp.getParameterDefinitions().get(0).getClass());
        BooleanParameterDefinition bpd = (BooleanParameterDefinition) pdp.getParameterDefinitions().get(0);
        assertEquals("flag", bpd.getName());
        assertTrue(bpd.isDefaultValue());
        // TODO JENKINS-29711 means it seems to omit the required () but we are not currently testing the Snippetizer output anyway
    }

    @Ignore("TODO as above")
    @SuppressWarnings("rawtypes")
    @Test public void configRoundTripBuildDiscarder() throws Exception {
        StepConfigTester tester = new StepConfigTester(r);
        assertEquals(Collections.emptyList(), tester.configRoundTrip(new JobPropertyStep(Collections.<JobProperty>emptyList())).getProperties());
        List<JobProperty> properties = tester.configRoundTrip(new JobPropertyStep(Collections.<JobProperty>singletonList(new BuildDiscarderProperty(new LogRotator(1, 2, -1, 3))))).getProperties();
        assertEquals(1, properties.size());
        assertEquals(BuildDiscarderProperty.class, properties.get(0).getClass());
        BuildDiscarderProperty bdp = (BuildDiscarderProperty) properties.get(0);
        BuildDiscarder strategy = bdp.getStrategy();
        assertNotNull(strategy);
        assertEquals(LogRotator.class, strategy.getClass());
        LogRotator lr = (LogRotator) strategy;
        assertEquals(1, lr.getDaysToKeep());
        assertEquals(2, lr.getNumToKeep());
        assertEquals(-1, lr.getArtifactDaysToKeep());
        assertEquals(3, lr.getArtifactNumToKeep());
    }

    @Issue("JENKINS-30206")
    @Test public void useParameter() throws Exception {
        sampleRepo.init();
        ScriptApproval.get().approveSignature("method groovy.lang.Binding hasVariable java.lang.String"); // TODO add to generic whitelist
        sampleRepo.write("Jenkinsfile",
                "properties([[$class: 'ParametersDefinitionProperty', parameterDefinitions: [[$class: 'StringParameterDefinition', name: 'myparam', defaultValue: 'default value']]]])\n" +
                "echo \"received ${binding.hasVariable('myparam') ? myparam : 'undefined'}\"");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        // TODO not all that satisfactory since it means you cannot rely on a default value; would be a little easier given JENKINS-27295
        r.assertLogContains("received undefined", b1);
        WorkflowRun b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("myparam", "special value"))));
        assertEquals(2, b2.getNumber());
        r.assertLogContains("received special value", b2);
    }

    @SuppressWarnings("deprecation") // RunList.size
    @Test public void useBuildDiscarder() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '1']]])");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity(); // #1 built automatically
        assertEquals(1, p.getBuilds().size());
        r.assertBuildStatusSuccess(p.scheduleBuild2(0)); // #2
        assertEquals(1, p.getBuilds().size());
        r.assertBuildStatusSuccess(p.scheduleBuild2(0)); // #3
        assertEquals(1, p.getBuilds().size());
        WorkflowRun b3 = p.getLastBuild();
        assertEquals(3, b3.getNumber());
        assertNull(b3.getPreviousBuild());
    }

}
