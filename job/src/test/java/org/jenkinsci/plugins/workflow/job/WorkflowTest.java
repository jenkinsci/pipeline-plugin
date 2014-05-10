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

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.io.FileUtils;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests of workflows that involve restarting Jenkins in the middle.
 */
public class WorkflowTest extends SingleJobTestBase {

    /**
     * Restart Jenkins while workflow is executing to make sure it suspends all right
     */
    @Test public void demo() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = story.j.jenkins.createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition("dsl.watch(new File('" + story.j.jenkins.getRootDir() + "/touch'))"));
                startBuilding();
                waitForWorkflowToSuspend();
                assertTrue(b.isBuilding());
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                assertThatWorkflowIsSuspended();
                FileUtils.write(new File(story.j.jenkins.getRootDir(), "touch"), "I'm here");
                watchDescriptor.watchUpdate();
                e.waitForSuspension();
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
                story.j.jenkins.setNumExecutors(0);
                DumbSlave s = createSlave(story.j);
                String nodeName = s.getNodeName();

                p = story.j.jenkins.createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                    "def s = jenkins.model.Jenkins.instance.getComputer('" + nodeName + "')\n" +
                    "def r = s.node.rootPath\n" +
                    "def p = r.getRemote()\n" +

                    "dsl.watch(new File('" + story.j.jenkins.getRootDir() + "/touch'))\n" +

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

                FileUtils.write(new File(story.j.jenkins.getRootDir(), "touch"), "I'm here");

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

                p = story.j.jenkins.createProject(WorkflowJob.class, "demo");
                dir.set(s.getRemoteFS() + "/workspace/" + p.getFullName());
                p.setDefinition(new CpsFlowDefinition(
                    "dsl.node('" + s.getNodeName() + "') {\n" +
                    "    dsl.sh('echo before=$PWD')\n" +
                    "    dsl.sh('echo ONSLAVE=$ONSLAVE')\n" +

                        // we'll suspend the execution here
                    "    dsl.watch(new File('" + story.j.jenkins.getRootDir() + "/touch'))\n" +

                    "    dsl.sh('echo after=$PWD')\n" +
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

                FileUtils.write(new File(story.j.jenkins.getRootDir(), "touch"), "I'm here");

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

                p = story.j.jenkins.createProject(WorkflowJob.class, "demo");
                dir.set(s.getRemoteFS() + "/workspace/" + p.getFullName());
                p.setDefinition(new CpsFlowDefinition(
                    "dsl.node('" + s.getNodeName() + "') {\n" +
                    "    dsl.sh('pwd; echo ONSLAVE=$ONSLAVE')\n" +
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

    /**
     * ability to invoke body needs to survive beyond Jenkins restart.
     */
    @Test public void invokeBodyLaterAfterRestart() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = story.j.jenkins.createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                    "int count=0;\n" +
                    "dsl.retry(3) {\n" +
                        // we'll suspend the execution here
                    "    dsl.watch(new File('" + story.j.jenkins.getRootDir() + "/touch'))\n" +

                    "    if (count++ < 2) {\n" + // forcing retry
                    "        throw new IllegalArgumentException();\n" +
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
                FileUtils.write(new File(story.j.jenkins.getRootDir(), "touch"), "I'm here");

                while (!e.isComplete()) {
                    e.waitForSuspension();
                }

                assertTrue(e.programPromise.get().closures.isEmpty());

                assertBuildCompletedSuccessfully();
            }
        });
    }

}
