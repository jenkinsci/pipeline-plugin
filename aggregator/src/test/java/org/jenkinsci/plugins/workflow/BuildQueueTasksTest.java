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

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;

import com.gargoylesoftware.htmlunit.Page;

import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class BuildQueueTasksTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-28649")
    @Test public void queueAPI() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        // try to use non-existent node to keep the build queued
        p.setDefinition(new CpsFlowDefinition("node('linux') { echo 'test' }"));
        QueueTaskFuture<WorkflowRun> build = p.scheduleBuild2(0);

        WorkflowRun b = build.getStartCondition().get();
        CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();
        e.waitForSuspension();

        JenkinsRule.WebClient wc = r.createWebClient();
        Page queue = wc.goTo("queue/api/json", "application/json");

        JSONObject o = JSONObject.fromObject(queue.getWebResponse().getContentAsString());
        JSONArray items = o.getJSONArray("items");
        assertEquals(1, items.size());
        assertEquals(true, items.getJSONObject(0).getBoolean("stuck"));
        assertEquals(true, items.getJSONObject(0).getBoolean("buildable"));

        e.interrupt(Result.ABORTED);

        queue = wc.goTo("queue/api/json", "application/json");
        o = JSONObject.fromObject(queue.getWebResponse().getContentAsString());
        items = o.getJSONArray("items");

        assertEquals(0, items.size());
    }

}
