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

package org.jenkinsci.plugins.workflow.job;

import hudson.model.Node;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

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
                assertTrue(e.isComplete());
                assertBuildCompletedSuccessfully();
            }
        });
    }

    /**
     * Workflow captures a stateful object, and we verify that it survives the restart
     */
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

                assertTrue(JenkinsRule.getLog(b), b.isBuilding());
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                assertThatWorkflowIsSuspended();

                FileUtils.write(new File(jenkins().getRootDir(), "touch"), "I'm here");

                watchDescriptor.watchUpdate();

                e.waitForSuspension();
                System.out.println(JenkinsRule.getLog(b));
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
    @Test public void buildShellScriptOnSlave() throws Exception {
        final AtomicReference<String> dir = new AtomicReference<String>();
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                DumbSlave s = createSlave(story.j);
                s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONSLAVE", "true")));

                p = jenkins().createProject(WorkflowJob.class, "demo");
                dir.set(s.getRemoteFS() + "/workspace/" + p.getFullName());
                p.setDefinition(new CpsFlowDefinition(
                    "node('" + s.getNodeName() + "') {\n" +
                    "    sh('echo before=$PWD')\n" +
                    "    sh('echo ONSLAVE=$ONSLAVE')\n" +

                        // we'll suspend the execution here
                    "    watch(new File('" + jenkins().getRootDir() + "/touch'))\n" +

                    "    sh('echo after=$PWD')\n" +
                    "}"));

                startBuilding();

                // wait until the execution gets to the watch task
                while (watchDescriptor.getActiveWatches().isEmpty()) {
                    assertTrue(JenkinsRule.getLog(b), b.isBuilding());
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

                story.j.assertLogContains("before=" + dir, b);
                story.j.assertLogContains("ONSLAVE=true", b);
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

    @Test public void acquireWorkspace() throws Exception {
        final AtomicReference<String> dir = new AtomicReference<String>();
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                @SuppressWarnings("deprecation")
                String slaveRoot = story.j.createTmpDir().getPath();
                jenkins().addNode(new DumbSlave("slave", "dummy", slaveRoot, "2", Node.Mode.NORMAL, "", story.j.createComputerLauncher(null), RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList()));
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("FLAG", null)));
                dir.set(slaveRoot + "/workspace/" + p.getFullName());
                p.setDefinition(new CpsFlowDefinition(
                        "node('slave') {\n" + // this locks the WS
                                "    sh('echo default=$PWD')\n" +
                                "    ws {\n" + // and this locks a second one
                                "        sh('echo before=$PWD')\n" +
                                "        watch(new File('" + jenkins().getRootDir() + "', FLAG))\n" +
                                "        sh('echo after=$PWD')\n" +
                                "    }\n" +
                                "}"
                ));
                p.save();
                WorkflowRun b1 = p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("FLAG", "one"))).waitForStart();
                CpsFlowExecution e1 = (CpsFlowExecution) b1.getExecutionPromise().get();
                while (watchDescriptor.getActiveWatches().isEmpty()) {
                    assertTrue(JenkinsRule.getLog(b1), b1.isBuilding());
                    waitForWorkflowToSuspend(e1);
                }
                WorkflowRun b2 = p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("FLAG", "two"))).waitForStart();
                CpsFlowExecution e2 = (CpsFlowExecution) b2.getExecutionPromise().get();
                while (watchDescriptor.getActiveWatches().size() == 1) {
                    assertTrue(JenkinsRule.getLog(b2), b2.isBuilding());
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
                story.j.assertLogContains("default=" + dir, b1);
                story.j.assertLogContains("before=" + dir + "@2", b1);
                story.j.assertLogContains("after=" + dir + "@2", b1);
                story.j.assertLogContains("default=" + dir + "@3", b2);
                story.j.assertLogContains("before=" + dir + "@4", b2);
                story.j.assertLogContains("after=" + dir + "@4", b2);
                FileUtils.write(new File(jenkins().getRootDir(), "three"), "here");
                WorkflowRun b3 = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("FLAG", "three"))));
                story.j.assertLogContains("default=" + dir, b3);
                story.j.assertLogContains("before=" + dir + "@2", b3);
                story.j.assertLogContains("after=" + dir + "@2", b3);
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
                    "import org.jenkinsci.plugins.workflow.job.SimulatedFailureForRetry;\n"+
                    "int count=0;\n" +
                    "retry(3) {\n" +
                        // we'll suspend the execution here
                    "    watch(new File('" + jenkins().getRootDir() + "/touch'))\n" +

                    "    if (count++ < 2) {\n" + // forcing retry
                    "        throw new SimulatedFailureForRetry();\n" +
                    "    }\n" +
                    "}"));

                startBuilding();

                // wait until the execution gets to the watch task
                while (watchDescriptor.getActiveWatches().isEmpty()) {
                    assertTrue(JenkinsRule.getLog(b), b.isBuilding());
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

}
