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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.xml.sax.SAXException;

import com.gargoylesoftware.htmlunit.Page;

import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class BuildQueueTasksTest {

    @Rule public RestartableJenkinsRule story = JenkinsRuleExt.workAroundJenkins30395Restartable();

    @Issue("JENKINS-28649")
    @Test public void queueAPI() throws Exception {
        // This is implicitly testing ExecutorStepExecution$PlaceholderTask as exported bean
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                // use non-existent node label to keep the build queued
                p.setDefinition(new CpsFlowDefinition("node('nonexistent') { echo 'test' }"));

                WorkflowRun b = scheduleAndWaitQueued(p);
                assertQueueAPIStatusOKAndAbort(b);
            }
        });
    }

    @Issue("JENKINS-28649")
    @Test public void queueAPIRestartable() throws Exception {
        // This is implicitly testing AfterRestartTask as exported bean
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                // use non-existent node label to keep the build queued
                p.setDefinition(new CpsFlowDefinition("node('nonexistent') { echo 'test' }"));
                scheduleAndWaitQueued(p);
                // Ok, the item is in he queue now, restart
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);

                assertQueueAPIStatusOKAndAbort(b);
            }
        });
    }

    @Issue("JENKINS-28649")
    @Test public void computerAPI() throws Exception {
        // This is implicitly testing ExecutorStepExecution$PlaceholderTask$PlaceholderExecutable as exported bean
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "node {\n" +
                        "  echo 'test'\n " +
                        "  semaphore 'watch'\n " +
                        "}"));

                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("watch/1", b);

                assertComputerAPIStatusOK();

                SemaphoreStep.success("watch/1", null);
            }
        });
    }

    @Issue("JENKINS-28649")
    @Test public void computerAPIRestartable() throws Exception {
        // This is implicitly testing AfterRestartTask#run as exported bean
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("semaphore 'watch'"));

                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("watch/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assertComputerAPIStatusOK();

                SemaphoreStep.success("watch/1", null);
            }
        });
    }

    private WorkflowRun scheduleAndWaitQueued(WorkflowJob p) throws InterruptedException, ExecutionException {
        QueueTaskFuture<WorkflowRun> build = p.scheduleBuild2(0);

        WorkflowRun b = build.getStartCondition().get();
        int secondsWaiting = 0;
        while (true) {
            if (secondsWaiting > 5) {
                assertTrue("No item queued after 5 seconds", false);
            }
            if (story.j.jenkins.getQueue().getItems().length > 0) {
                break;
            }
            Thread.sleep(1000);
            secondsWaiting++;
        }
        return b;
    }

    private void assertQueueAPIStatusOKAndAbort(WorkflowRun b)
            throws IOException, SAXException, InterruptedException, ExecutionException {
        JenkinsRule.WebClient wc = story.j.createWebClient();
        Page queue = wc.goTo("queue/api/json", "application/json");

        JSONObject o = JSONObject.fromObject(queue.getWebResponse().getContentAsString());
        JSONArray items = o.getJSONArray("items");
        // Just check that the request returns HTTP 200 and there is some content. 
        // Not going into de the content in this test
        assertEquals(1, items.size());

        CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();
        e.interrupt(Result.ABORTED);

        queue = wc.goTo("queue/api/json", "application/json");
        o = JSONObject.fromObject(queue.getWebResponse().getContentAsString());
        items = o.getJSONArray("items");

        assertEquals(0, items.size());
    }

    private void assertComputerAPIStatusOK() throws IOException, SAXException {
        JenkinsRule.WebClient wc = story.j.createWebClient();
        Page queue = wc.goTo("computer/api/json?tree=computer[executors[*]]", "application/json");

        JSONObject o = JSONObject.fromObject(queue.getWebResponse().getContentAsString());
        JSONArray computers = o.getJSONArray("computer");
        // Just check that the request returns HTTP 200 and there is some content.
        // Not going into de the content in this test
        assertEquals(1, computers.size());
    }

}
