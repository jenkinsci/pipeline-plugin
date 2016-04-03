/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow;

import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Result;
import jenkins.triggers.ReverseBuildTrigger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestExtension;

/** Integration test for special behavior of {@link ReverseBuildTrigger} with {@link WorkflowJob}. */
public class ReverseBuildTriggerTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Ignore("TODO fails without https://github.com/jenkinsci/jenkins/pull/2207")
    @Issue("JENKINS-33971")
    @Test public void upstreamMapRebuilding() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob us = story.j.jenkins.createProject(WorkflowJob.class, "us");
                us.setDefinition(new CpsFlowDefinition(""));
                us.addProperty(new SlowToLoad()); // force it to load after ds when we restart
                WorkflowJob ds = story.j.jenkins.createProject(WorkflowJob.class, "ds");
                ds.setDefinition(new CpsFlowDefinition(""));
                ds.addTrigger(new ReverseBuildTrigger("us", Result.SUCCESS));
                story.j.assertBuildStatusSuccess(us.scheduleBuild2(0));
                story.j.waitUntilNoActivity();
                WorkflowRun ds1 = ds.getLastCompletedBuild();
                assertNotNull(ds1);
                assertEquals(1, ds1.getNumber());
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob us = story.j.jenkins.getItemByFullName("us", WorkflowJob.class);
                assertNotNull(us);
                WorkflowJob ds = story.j.jenkins.getItemByFullName("ds", WorkflowJob.class);
                assertNotNull(ds);
                story.j.assertBuildStatusSuccess(us.scheduleBuild2(0));
                story.j.waitUntilNoActivity();
                WorkflowRun ds2 = ds.getLastCompletedBuild();
                assertNotNull(ds2);
                assertEquals(2, ds2.getNumber());
            }
        });
    }
    public static class SlowToLoad extends JobProperty<WorkflowJob> {
        @Override protected void setOwner(WorkflowJob owner) {
            super.setOwner(owner);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException x) {
                throw new AssertionError(x);
            }
        }
        @TestExtension("upstreamMapRebuilding") public static class DescriptorImpl extends JobPropertyDescriptor {}
    }

}
