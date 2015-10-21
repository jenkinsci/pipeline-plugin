/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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

import com.google.common.base.Function;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.model.User;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.support.actions.EnvironmentAction;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Tests of workflows that involve restarting Jenkins in the middle.
 */
public class WorkflowTest extends SingleJobTestBase {

    /**
     * Restart Jenkins while workflow is executing to make sure it suspends all right
     */
    @Test public void demo() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition("semaphore 'wait'"));
                startBuilding();
                SemaphoreStep.waitForStart("wait/1", b);
                assertTrue(b.isBuilding());
                liveness();
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                rebuildContext(story.j);
                assertThatWorkflowIsSuspended();
                for (int i = 0; i < 600 && !Queue.getInstance().isEmpty(); i++) {
                    Thread.sleep(100);
                }
                liveness();
                SemaphoreStep.success("wait/1", null);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
            }
        });
    }
    private void liveness() {
        assertFalse(jenkins().toComputer().isIdle());
        Executor e = b.getOneOffExecutor();
        assertNotNull(e);
        assertEquals(e, b.getExecutor());
        assertTrue(e.isActive());
        assertFalse(e.isAlive());
    }

    /**
     * ability to invoke body needs to survive beyond Jenkins restart.
     */
    @Test public void invokeBodyLaterAfterRestart() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                    "int count=0;\n" +
                    "retry(3) {\n" +
                    "    semaphore 'wait'\n" +
                    "    if (count++ < 2) {\n" + // forcing retry
                    "        error 'died'\n" +
                    "    }\n" +
                    "}"));

                startBuilding();
                SemaphoreStep.waitForStart("wait/1", b);
                assertTrue(b.isBuilding());
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                assertThatWorkflowIsSuspended();

                // resume execution and cause the retry to invoke the body again
                SemaphoreStep.success("wait/1", null);
                SemaphoreStep.success("wait/2", null);
                SemaphoreStep.success("wait/3", null);

                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                assertTrue(e.programPromise.get().closures.isEmpty());
            }
        });
    }

    @Test public void authentication() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                jenkins().setSecurityRealm(story.j.createDummySecurityRealm());
                jenkins().save();
                QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(new MockQueueItemAuthenticator(Collections.singletonMap("demo", User.get("someone").impersonate())));
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition("checkAuth()"));
                ScriptApproval.get().preapproveAll();
                startBuilding();
                waitForWorkflowToSuspend();
                assertTrue(b.isBuilding());
                story.j.waitForMessage("running as someone", b);
                CheckAuth.finish(false);
                waitForWorkflowToSuspend();
                assertTrue(b.isBuilding());
                story.j.waitForMessage("still running as someone", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assertEquals(JenkinsRule.DummySecurityRealm.class, jenkins().getSecurityRealm().getClass());
                rebuildContext(story.j);
                assertThatWorkflowIsSuspended();
                story.j.waitForMessage("again running as someone", b);
                CheckAuth.finish(true);
                story.j.assertLogContains("finally running as someone", story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b)));
            }
        });
    }
    public static final class CheckAuth extends AbstractStepImpl {
        @DataBoundConstructor public CheckAuth() {}
        @TestExtension("authentication") public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
            public DescriptorImpl() {
                super(Execution.class);
            }
            @Override public String getFunctionName() {
                return "checkAuth";
            }
            @Override
            public String getDisplayName() {
                return getFunctionName(); // TODO would be nice for this to be the default, perhaps?
            }
        }
        public static final class Execution extends AbstractStepExecutionImpl {
            @StepContextParameter transient TaskListener listener;
            @StepContextParameter transient FlowExecution flow;
            @Override public boolean start() throws Exception {
                listener.getLogger().println("running as " + Jenkins.getAuthentication().getName() + " from " + Thread.currentThread().getName());
                return false;
            }
            @Override public void stop(Throwable cause) throws Exception {}
            @Override public void onResume() {
                super.onResume();
                try {
                    listener.getLogger().println("again running as " + flow.getAuthentication().getName() + " from " + Thread.currentThread().getName());
                } catch (Exception x) {
                    getContext().onFailure(x);
                }
            }
        }
        public static void finish(final boolean terminate) {
            StepExecution.applyAll(Execution.class, new Function<Execution,Void>() {
                @Override public Void apply(Execution input) {
                    try {
                        input.listener.getLogger().println((terminate ? "finally" : "still") + " running as " + input.flow.getAuthentication().getName() + " from " + Thread.currentThread().getName());
                        if (terminate) {
                            input.getContext().onSuccess(null);
                        }
                    } catch (Exception x) {
                        input.getContext().onFailure(x);
                    }
                    return null;
                }
            });
        }
    }

    @Issue("JENKINS-30122")
    @Test public void authenticationInSynchronousStep() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                jenkins().setSecurityRealm(story.j.createDummySecurityRealm());
                jenkins().save();
                QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(new MockQueueItemAuthenticator(Collections.singletonMap("demo", User.get("someone").impersonate())));
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition("echo \"ran as ${auth()}\"", true));
                b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("ran as someone", b);
            }
        });
    }
    public static final class CheckAuthSync extends AbstractStepImpl {
        @DataBoundConstructor public CheckAuthSync() {}
        @TestExtension("authenticationInSynchronousStep") public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
            public DescriptorImpl() {
                super(Execution.class);
            }
            @Override public String getFunctionName() {
                return "auth";
            }
            @Override public String getDisplayName() {
                return getFunctionName();
            }
        }
        public static final class Execution extends AbstractSynchronousNonBlockingStepExecution<String> {
            @Override protected String run() throws Exception {
                return Jenkins.getAuthentication().getName();
            }
        }
    }

    @Test public void env() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                Map<String,String> slaveEnv = new HashMap<String,String>();
                slaveEnv.put("BUILD_TAG", null);
                slaveEnv.put("PERMACHINE", "set");
                JenkinsRuleExt.createSpecialEnvSlave(story.j, "slave", null, slaveEnv);
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition("node('slave') {\n"
                        + "  sh 'echo tag=$BUILD_TAG PERMACHINE=$PERMACHINE'\n"
                        + "  env.BUILD_TAG='custom'\n"
                        + "  sh 'echo tag2=$BUILD_TAG'\n"
                        + "  env.STUFF='more'\n"
                        + "  semaphore 'env'\n"
                        + "  env.BUILD_TAG=\"${env.BUILD_TAG}2\"\n"
                        + "  sh 'echo tag3=$BUILD_TAG stuff=$STUFF'\n"
                        + "  env.PATH=\"/opt/stuff/bin:${env.PATH}\"\n"
                        + "  sh 'echo shell PATH=$PATH'\n"
                        + "  echo \"groovy PATH=${env.PATH}\""
                        + "}", true));
                startBuilding();
                SemaphoreStep.waitForStart("env/1", b);
                assertTrue(b.isBuilding());
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                assertThatWorkflowIsSuspended();
                SemaphoreStep.success("env/1", null);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                story.j.assertLogContains("tag=jenkins-demo-1 PERMACHINE=set", b);
                story.j.assertLogContains("tag2=custom", b);
                story.j.assertLogContains("tag3=custom2 stuff=more", b);
                story.j.assertLogContains("shell PATH=/opt/stuff/bin:", b);
                story.j.assertLogContains("groovy PATH=/opt/stuff/bin:", b);
                EnvironmentAction a = b.getAction(EnvironmentAction.class);
                assertNotNull(a);
                assertEquals("custom2", a.getEnvironment().get("BUILD_TAG"));
                assertEquals("more", a.getEnvironment().get("STUFF"));
                assertNotNull(a.getEnvironment().get("PATH"));
            }
        });
    }

}
