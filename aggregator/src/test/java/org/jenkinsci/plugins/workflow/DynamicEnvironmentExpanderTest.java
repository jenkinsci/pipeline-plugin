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

import hudson.EnvVars;
import java.io.IOException;
import java.io.Serializable;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class DynamicEnvironmentExpanderTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Ignore("TODO onResume is not called at all")
    @Issue("JENKINS-26163")
    @Test public void dynamics() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("dynamicEnv {echo \"initially ${env.DYNVAR}\"; semaphore 'wait'; echo \"subsequently ${env.DYNVAR}\"}"));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
                story.j.waitForMessage("initially one", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                SemaphoreStep.success("wait/1", null);
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                story.j.assertLogContains("subsequently two", story.j.waitForCompletion(b));
            }
        });
    }
    public static class DynamicEnvStep extends AbstractStepImpl {
        @DataBoundConstructor public DynamicEnvStep() {}
        public static class Execution extends AbstractStepExecutionImpl {
            @Override public boolean start() throws Exception {
                StepContext context = getContext();
                State state = new State("one");
                context.newBodyInvoker().
                        // State object saved in two ways, but should be the same instance:
                        withContexts(state, EnvironmentExpander.merge(context.get(EnvironmentExpander.class), new ExpanderImpl(state))).
                        withCallback(BodyExecutionCallback.wrap(context)).
                        withDisplayName(null).start();
                return false;
            }
            @Override public void onResume() {
                try {
                    StepContext context = getContext();
                    // Matches withContexts call above:
                    State state = context.get(State.class);
                    System.err.printf("resuming @%h%n", state);
                    state.value = "two";
                    context.saveState();
                } catch (Exception x) {
                    throw new AssertionError(x);
                }
            }
            @Override public void stop(Throwable cause) throws Exception {}
        }
        private static class State implements Serializable {
            String value;
            State(String value) {
                this.value = value;
                System.err.printf("constructing %s @%h%n", value, this);
            }
            private Object readResolve() {
                System.err.printf("deserializing %s @%h%n", value, this);
                return this;
            }
        }
        private static class ExpanderImpl extends EnvironmentExpander {
            private final State state;
            ExpanderImpl(State state) {
                this.state = state;
            }
            @Override public void expand(EnvVars env) throws IOException, InterruptedException {
                env.override("DYNVAR", state.value);
            }
        }
        @TestExtension("dynamics") public static class DescriptorImpl extends AbstractStepDescriptorImpl {
            public DescriptorImpl() {
                super(Execution.class);
            }
            @Override public String getFunctionName() {return "dynamicEnv";}
            @Override public String getDisplayName() {return getFunctionName();}
            @Override public boolean takesImplicitBlockArgument() {return true;}
        }
    }

}
