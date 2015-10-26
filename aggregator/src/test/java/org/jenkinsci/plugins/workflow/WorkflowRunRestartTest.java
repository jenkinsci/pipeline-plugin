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

package org.jenkinsci.plugins.workflow;

import com.google.inject.Inject;
import hudson.model.Executor;
import hudson.model.Result;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class WorkflowRunRestartTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Issue("JENKINS-25550")
    @Test public void hardKill() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("def seq = 0; retry (99) {zombie id: ++seq}"));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                story.j.waitForMessage("[1] undead", b);
                Executor ex = b.getExecutor();
                assertNotNull(ex);
                ex.interrupt();
                story.j.waitForMessage("[1] bwahaha FlowInterruptedException #1", b);
                ex.interrupt();
                story.j.waitForMessage("[1] bwahaha FlowInterruptedException #2", b);
                b.doTerm();
                story.j.waitForMessage("[2] undead", b);
                ex.interrupt();
                story.j.waitForMessage("[2] bwahaha FlowInterruptedException #1", b);
                b.doKill();
                story.j.waitForMessage("Hard kill!", b);
                story.j.waitForCompletion(b);
                story.j.assertBuildStatus(Result.ABORTED, b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                assertFalse(b.isBuilding());
            }
        });
    }
    public static class Zombie extends AbstractStepImpl {
        @DataBoundSetter public int id;
        @DataBoundConstructor public Zombie() {}
        public static class Execution extends AbstractStepExecutionImpl {
            @Inject(optional=true) private transient Zombie step;
            @StepContextParameter private transient TaskListener listener;
            int id;
            int count;
            @Override public boolean start() throws Exception {
                id = step.id;
                listener.getLogger().printf("[%d] undead%n", id);
                return false;
            }
            @Override public void stop(Throwable cause) throws Exception {
                listener.getLogger().printf("[%d] bwahaha %s #%d%n", id, cause.getClass().getSimpleName(), ++count);
            }

        }
        @TestExtension("hardKill") public static class DescriptorImpl extends AbstractStepDescriptorImpl {
            public DescriptorImpl() {
                super(Execution.class);
            }
            @Override public String getFunctionName() {
                return "zombie";
            }
            @Override public String getDisplayName() {
                return "zombie";
            }
        }
    }

}
