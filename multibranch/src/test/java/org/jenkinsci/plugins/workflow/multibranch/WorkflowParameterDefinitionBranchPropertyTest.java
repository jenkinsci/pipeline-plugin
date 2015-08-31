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

import hudson.model.DescriptorVisibilityFilter;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchPropertyDescriptor;
import jenkins.branch.BranchSource;
import jenkins.branch.BuildRetentionBranchProperty;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.branch.RateLimitBranchProperty;
import jenkins.plugins.git.GitSCMSource;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectTest.findBranchProject;
import org.jenkinsci.plugins.workflow.steps.scm.GitSampleRepoRule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class WorkflowParameterDefinitionBranchPropertyTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Test public void propertyVisible() throws Exception {
        WorkflowMultiBranchProject p = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        Set<Class<? extends BranchProperty>> clazzes = new HashSet<Class<? extends BranchProperty>>();
        for (BranchPropertyDescriptor d : DescriptorVisibilityFilter.apply(p, BranchPropertyDescriptor.all())) {
            clazzes.add(d.clazz);
        }
        @SuppressWarnings("unchecked")
        Set<Class<? extends BranchProperty>> expected = new HashSet<Class<? extends BranchProperty>>(Arrays.asList(
                WorkflowParameterDefinitionBranchProperty.class,
                RateLimitBranchProperty.class,
                BuildRetentionBranchProperty.class
                /* UntrustedBranchProperty should not be here! */));
        assertEquals(expected, clazzes);
    }

    @Issue("JENKINS-30206")
    @Test public void useParameter() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"received ${myparam}\"");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        WorkflowParameterDefinitionBranchProperty prop = new WorkflowParameterDefinitionBranchProperty();
        prop.setParameterDefinitions(Collections.<ParameterDefinition>singletonList(new StringParameterDefinition("myparam", "default value")));
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[] {prop})));
        WorkflowJob p = findBranchProject(mp, "master");
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        r.assertLogContains("received default value", b1);
        WorkflowRun b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("myparam", "special value"))));
        assertEquals(2, b2.getNumber());
        r.assertLogContains("received special value", b2);
    }

}
