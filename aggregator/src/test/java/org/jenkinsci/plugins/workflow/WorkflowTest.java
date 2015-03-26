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
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.model.labels.LabelAtom;
import hudson.remoting.Launcher;
import hudson.remoting.Which;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.util.JavaEnvUtils;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep;
import org.jenkinsci.plugins.workflow.support.actions.EnvironmentAction;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.RandomlyFails;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Tests of workflows that involve restarting Jenkins in the middle.
 */
public class WorkflowTest extends SingleJobTestBase {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Restart Jenkins while workflow is executing to make sure it suspends all right
     */
    @Test public void demo() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition("watch(new File('" + jenkins().getRootDir() + "/touch'))"));
                startBuilding();
                waitForWorkflowToSuspend();
                assertTrue(b.isBuilding());
                assertFalse(jenkins().toComputer().isIdle());
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
                assertFalse(jenkins().toComputer().isIdle());
                FileUtils.write(new File(jenkins().getRootDir(), "touch"), "I'm here");
                watchDescriptor.watchUpdate();
                waitForWorkflowToComplete();
                assertBuildCompletedSuccessfully();
            }
        });
    }

    /**
     * Workflow captures a stateful object, and we verify that it survives the restart
     */
    @RandomlyFails("TODO observed !e.complete")
    @Test public void persistEphemeralObject() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                jenkins().setNumExecutors(0);
                DumbSlave s = createSlave(story.j);
                String nodeName = s.getNodeName();

                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                    "def s = jenkins.model.Jenkins.instance.getComputer('" + nodeName + "')\n" +
                    "def r = s.node.rootPath\n" +
                    "def p = r.getRemote()\n" +

                    "watch(new File('" + jenkins().getRootDir() + "/touch'))\n" +

                    // make sure these values are still alive
                    "assert s.nodeName=='" + nodeName + "'\n" +
                    "assert r.getRemote()==p : r.getRemote() + ' vs ' + p;\n" +
                    "assert r.channel==s.channel : r.channel.toString() + ' vs ' + s.channel\n"));

                startBuilding();
                waitForWorkflowToSuspend();

                assertTrue(b.isBuilding());
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                assertThatWorkflowIsSuspended();

                FileUtils.write(new File(jenkins().getRootDir(), "touch"), "I'm here");

                watchDescriptor.watchUpdate();

                e.waitForSuspension();
                assertTrue(e.isComplete());

                assertBuildCompletedSuccessfully();
            }
        });
    }

    /**
     * Executes a shell script build on a slave.
     *
     * This ensures that the context variable overrides are working as expected, and
     * that they are persisted and resurrected.
     */
    @RandomlyFails("TODO assertBuildCompletedSuccessfully sometimes fails even though Allocate node : End has been printed")
    @Test public void buildShellScriptOnSlave() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                DumbSlave s = createSlave(story.j);
                s.setLabelString("remote quick");
                s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONSLAVE", "true")));

                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                    "node('" + s.getNodeName() + "') {\n" +
                    // TODO this has been observed to print the basename command, but not echo the result; why?
                    "    sh('echo before=`basename $PWD`')\n" +
                    "    sh('echo ONSLAVE=$ONSLAVE')\n" +

                        // we'll suspend the execution here
                    "    watch(new File('" + jenkins().getRootDir() + "/touch'))\n" +

                    "    sh('echo after=$PWD')\n" +
                    "}"));

                startBuilding();

                // wait until the execution gets to the watch task
                while (watchDescriptor.getActiveWatches().isEmpty()) {
                    assertTrue(b.isBuilding());
                    waitForWorkflowToSuspend();
                }
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                assertThatWorkflowIsSuspended();

                FileUtils.write(new File(jenkins().getRootDir(), "touch"), "I'm here");

                while (!e.isComplete()) {
                    e.waitForSuspension();
                }

                assertBuildCompletedSuccessfully();

                story.j.assertLogContains("before=demo", b);
                story.j.assertLogContains("ONSLAVE=true", b);

                FlowGraphWalker walker = new FlowGraphWalker(e);
                List<WorkspaceAction> actions = new ArrayList<WorkspaceAction>();
                for (FlowNode n = walker.next(); n != null; n = walker.next()) {
                    WorkspaceAction a = n.getAction(WorkspaceAction.class);
                    if (a != null) {
                        actions.add(a);
                    }
                }
                assertEquals(1, actions.size());
                assertEquals(new HashSet<LabelAtom>(Arrays.asList(LabelAtom.get("remote"), LabelAtom.get("quick"))), actions.get(0).getLabels());
            }
        });
    }

    @Ignore("TODO breaks because flows resumed too early and Jenkins.instance == null")
    @Test public void buildShellScriptOnSlaveWithDifferentResumePoint() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                String script = "node {watch(new File('" + jenkins().getRootDir() + "/touch'))}";
                p.setDefinition(new CpsFlowDefinition(script));
                startBuilding();
                waitForWorkflowToSuspend();
                // intentionally not waiting for watch step to begin
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                FileUtils.write(new File(jenkins().getRootDir(), "touch"), "");
                watchDescriptor.watchUpdate();
                waitForWorkflowToComplete();
                assertBuildCompletedSuccessfully();
            }
        });
    }

    private static Process jnlpProc;
    private void startJnlpProc() throws Exception {
        killJnlpProc();
        ProcessBuilder pb = new ProcessBuilder(JavaEnvUtils.getJreExecutable("java"), "-jar", Which.jarFile(Launcher.class).getAbsolutePath(), "-jnlpUrl", story.j.getURL() + "computer/dumbo/slave-agent.jnlp");
        try {
            ProcessBuilder.class.getMethod("inheritIO").invoke(pb);
        } catch (NoSuchMethodException x) {
            // prior to Java 7
        }
        System.err.println("Running: " + pb.command());
        jnlpProc = pb.start();
    }
    // TODO @After does not seem to work at all in RestartableJenkinsRule
    @AfterClass public static void killJnlpProc() {
        if (jnlpProc != null) {
            jnlpProc.destroy();
            jnlpProc = null;
        }
    }

    @RandomlyFails("TODO isBuilding assertion after restart occasionally fails; log ends with: ‘Running: Allocate node : Body : Start’ (no shell step in sight)")
    @Test public void buildShellScriptAcrossRestart() throws Exception {
        story.addStep(new Statement() {
            @SuppressWarnings("SleepWhileInLoop")
            @Override public void evaluate() throws Throwable {
                Logger LOGGER = Logger.getLogger(DurableTaskStep.class.getName());
                LOGGER.setLevel(Level.FINE);
                Handler handler = new ConsoleHandler();
                handler.setLevel(Level.ALL);
                LOGGER.addHandler(handler);
                // Cannot use regular JenkinsRule.createSlave due to JENKINS-26398.
                // Nor can we can use JenkinsRule.createComputerLauncher, since spawned commands are killed by CommandLauncher somehow (it is not clear how; apparently before its onClosed kills them off).
                DumbSlave s = new DumbSlave("dumbo", "dummy", tmp.getRoot().getAbsolutePath(), "1", Node.Mode.NORMAL, "", new JNLPLauncher(), RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList());
                story.j.jenkins.addNode(s);
                startJnlpProc();
                p = story.j.jenkins.createProject(WorkflowJob.class, "demo");
                File f1 = new File(story.j.jenkins.getRootDir(), "f1");
                File f2 = new File(story.j.jenkins.getRootDir(), "f2");
                new FileOutputStream(f1).close();
                p.setDefinition(new CpsFlowDefinition(
                    "node('dumbo') {\n" +
                    "    sh 'touch \"" + f2 + "\"; while [ -f \"" + f1 + "\" ]; do sleep 1; done; echo finished waiting; rm \"" + f2 + "\"'\n" +
                    "    echo 'OK, done'\n" +
                    "}"));
                startBuilding();
                while (!f2.isFile()) {
                    Thread.sleep(100);
                }
                assertTrue(b.isBuilding());
                killJnlpProc();
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                assertTrue(b.isBuilding());
                startJnlpProc(); // Have to relaunch JNLP agent, since the Jenkins port has changed, and we cannot force JenkinsRule to reuse the same port as before.
                File f1 = new File(story.j.jenkins.getRootDir(), "f1");
                File f2 = new File(story.j.jenkins.getRootDir(), "f2");
                assertTrue(f2.isFile());
                assertTrue(f1.delete());
                while (f2.isFile()) {
                    Thread.sleep(100);
                }
                story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b));
                story.j.assertLogContains("finished waiting", b);
                story.j.assertLogContains("OK, done", b);
                killJnlpProc();
            }
        });
    }

    @RandomlyFails("never printed 'finished waiting'")
    @Test public void buildShellScriptAcrossDisconnect() throws Exception {
        story.addStep(new Statement() {
            @SuppressWarnings("SleepWhileInLoop")
            @Override public void evaluate() throws Throwable {
                Logger LOGGER = Logger.getLogger(DurableTaskStep.class.getName());
                LOGGER.setLevel(Level.FINE);
                Handler handler = new ConsoleHandler();
                handler.setLevel(Level.ALL);
                LOGGER.addHandler(handler);
                DumbSlave s = new DumbSlave("dumbo", "dummy", tmp.getRoot().getAbsolutePath(), "1", Node.Mode.NORMAL, "", new JNLPLauncher(), RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList());
                story.j.jenkins.addNode(s);
                startJnlpProc();
                p = story.j.jenkins.createProject(WorkflowJob.class, "demo");
                File f1 = new File(story.j.jenkins.getRootDir(), "f1");
                File f2 = new File(story.j.jenkins.getRootDir(), "f2");
                new FileOutputStream(f1).close();
                p.setDefinition(new CpsFlowDefinition(
                    "node('dumbo') {\n" +
                    "    sh 'touch \"" + f2 + "\"; while [ -f \"" + f1 + "\" ]; do sleep 1; done; echo finished waiting; rm \"" + f2 + "\"'\n" +
                    "    echo 'OK, done'\n" +
                    "}"));
                startBuilding();
                while (!f2.isFile()) {
                    Thread.sleep(100);
                }
                assertTrue(b.isBuilding());
                Computer c = s.toComputer();
                assertNotNull(c);
                killJnlpProc();
                while (c.isOnline()) {
                    Thread.sleep(100);
                }
                startJnlpProc();
                while (c.isOffline()) {
                    Thread.sleep(100);
                }
                assertTrue(f2.isFile());
                assertTrue(f1.delete());
                while (f2.isFile()) {
                    Thread.sleep(100);
                }
                story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b));
                story.j.assertLogContains("finished waiting", b);
                story.j.assertLogContains("OK, done", b);
                killJnlpProc();
            }
        });
    }

    @Test public void buildShellScriptQuick() throws Exception {
        final AtomicReference<String> dir = new AtomicReference<String>();
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                DumbSlave s = createSlave(story.j);
                s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONSLAVE", "true")));

                p = jenkins().createProject(WorkflowJob.class, "demo");
                dir.set(s.getRemoteFS() + "/workspace/" + p.getFullName());
                p.setDefinition(new CpsFlowDefinition(
                    "node('" + s.getNodeName() + "') {\n" +
                    "    sh('pwd; echo ONSLAVE=$ONSLAVE')\n" +
                    "}"));

                startBuilding();

                while (!e.isComplete()) {
                    e.waitForSuspension();
                }

                assertBuildCompletedSuccessfully();

                story.j.assertLogContains(dir.get(), b);
                story.j.assertLogContains("ONSLAVE=true", b);
            }
        });
    }

    @RandomlyFails("TODO often basename is run but echo is not, or output lost; once got ‘InvalidClassException: cannot bind non-proxy descriptor to a proxy class’ inside BourneShellScript.doLaunch")
    @Test public void acquireWorkspace() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                @SuppressWarnings("deprecation")
                String slaveRoot = story.j.createTmpDir().getPath();
                jenkins().addNode(new DumbSlave("slave", "dummy", slaveRoot, "2", Node.Mode.NORMAL, "", story.j.createComputerLauncher(null), RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList()));
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("FLAG", null)));
                p.setDefinition(new CpsFlowDefinition(
                        "node('slave') {\n" + // this locks the WS
                                "    sh('echo default=`basename $PWD`')\n" +
                                "    ws {\n" + // and this locks a second one
                                "        sh('echo before=`basename $PWD`')\n" +
                                "        watch(new File('" + jenkins().getRootDir() + "', FLAG))\n" +
                                "        sh('echo after=`basename $PWD`')\n" +
                                "    }\n" +
                                "}"
                ));
                p.save();
                WorkflowRun b1 = p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("FLAG", "one"))).waitForStart();
                CpsFlowExecution e1 = (CpsFlowExecution) b1.getExecutionPromise().get();
                while (watchDescriptor.getActiveWatches().isEmpty()) {
                    assertTrue(b1.isBuilding());
                    waitForWorkflowToSuspend(e1);
                }
                WorkflowRun b2 = p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("FLAG", "two"))).waitForStart();
                CpsFlowExecution e2 = (CpsFlowExecution) b2.getExecutionPromise().get();
                while (watchDescriptor.getActiveWatches().size() == 1) {
                    assertTrue(b2.isBuilding());
                    waitForWorkflowToSuspend(e2);
                }
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                WorkflowRun b1 = p.getBuildByNumber(1);
                CpsFlowExecution e1 = (CpsFlowExecution) b1.getExecution();
                assertThatWorkflowIsSuspended(b1, e1);
                WorkflowRun b2 = p.getBuildByNumber(2);
                CpsFlowExecution e2 = (CpsFlowExecution) b2.getExecution();
                assertThatWorkflowIsSuspended(b2, e2);
                FileUtils.write(new File(jenkins().getRootDir(), "one"), "here");
                FileUtils.write(new File(jenkins().getRootDir(), "two"), "here");
                story.j.waitUntilNoActivity();
                assertBuildCompletedSuccessfully(b1);
                assertBuildCompletedSuccessfully(b2);
                story.j.assertLogContains("default=demo", b1);
                story.j.assertLogContains("before=demo@2", b1);
                story.j.assertLogContains("after=demo@2", b1);
                story.j.assertLogContains("default=demo@3", b2);
                story.j.assertLogContains("before=demo@4", b2);
                story.j.assertLogContains("after=demo@4", b2);
                FileUtils.write(new File(jenkins().getRootDir(), "three"), "here");
                WorkflowRun b3 = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("FLAG", "three"))));
                story.j.assertLogContains("default=demo", b3);
                story.j.assertLogContains("before=demo@2", b3);
                story.j.assertLogContains("after=demo@2", b3);
            }
        });
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
                        // we'll suspend the execution here
                    "    watch(new File('" + jenkins().getRootDir() + "/touch'))\n" +

                    "    if (count++ < 2) {\n" + // forcing retry
                    "        error 'died'\n" +
                    "    }\n" +
                    "}"));

                startBuilding();

                // wait until the execution gets to the watch task
                while (watchDescriptor.getActiveWatches().isEmpty()) {
                    assertTrue(b.isBuilding());
                    waitForWorkflowToSuspend();
                }
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                assertThatWorkflowIsSuspended();

                // resume execution and cause the retry to invoke the body again
                FileUtils.write(new File(jenkins().getRootDir(), "touch"), "I'm here");

                while (!e.isComplete()) {
                    e.waitForSuspension();
                }

                assertTrue(e.programPromise.get().closures.isEmpty());

                assertBuildCompletedSuccessfully();
            }
        });
    }

    @RandomlyFails("TODO does not pass reliably on CI; perhaps need different semaphores")
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
                JenkinsRuleExt.waitForMessage("running as someone", b);
                CheckAuth.finish(false);
                waitForWorkflowToSuspend();
                assertTrue(b.isBuilding());
                JenkinsRuleExt.waitForMessage("still running as someone", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assertEquals(JenkinsRule.DummySecurityRealm.class, jenkins().getSecurityRealm().getClass());
                rebuildContext(story.j);
                assertThatWorkflowIsSuspended();
                JenkinsRuleExt.waitForMessage("again running as someone", b);
                CheckAuth.finish(true);
                story.j.assertLogContains("finally running as someone", story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b)));
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

    @Test public void env() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition("node {\n"
                        + "  sh 'echo tag=$BUILD_TAG'\n"
                        + "  env.BUILD_TAG='custom'\n"
                        + "  sh 'echo tag2=$BUILD_TAG'\n"
                        + "  env.STUFF='more'\n"
                        + "  semaphore 'env'\n"
                        + "  env.BUILD_TAG=\"${env.BUILD_TAG}2\"\n"
                        + "  sh 'echo tag3=$BUILD_TAG stuff=$STUFF'\n"
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
                story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b));
                story.j.assertLogContains("tag=jenkins-demo-1", b);
                story.j.assertLogContains("tag2=custom", b);
                story.j.assertLogContains("tag3=custom2 stuff=more", b);
                EnvironmentAction a = b.getAction(EnvironmentAction.class);
                assertNotNull(a);
                assertEquals("custom2", a.getEnvironment().get("BUILD_TAG"));
                assertEquals("more", a.getEnvironment().get("STUFF"));
            }
        });
    }

    @RandomlyFails("TODO JENKINS-27532 sometimes two copies of the WorkflowRun are loaded")
    @Issue("JENKINS-26513")
    @Test public void executorStepRestart() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition("node('special') {echo 'OK ran'}"));
                startBuilding();
                JenkinsRuleExt.waitForMessage("Still waiting to schedule task", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                story.j.createSlave("special", null);
                rebuildContext(story.j);
                story.j.assertLogContains("OK ran", story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b)));
            }
        });
    }

}
