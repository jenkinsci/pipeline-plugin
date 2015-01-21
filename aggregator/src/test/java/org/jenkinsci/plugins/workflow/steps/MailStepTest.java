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
package org.jenkinsci.plugins.workflow.steps;

import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.mock_javamail.Mailbox;

import javax.mail.Message;
import javax.mail.internet.MimeMultipart;
import java.io.IOException;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class MailStepTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void test_missing_subject() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "workflow");
        // leave out the subject
        job.setDefinition(new CpsFlowDefinition("mail(to: 'tom.abcd@jenkins.org', body: 'body');", true));

        WorkflowRun run = jenkinsRule.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());
        jenkinsRule.assertLogContains("Email not sent. All mandatory properties must be supplied ('subject', 'body').", run);
    }

    @Test
    public void test_missing_body() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "workflow");
        // leave out the body
        job.setDefinition(new CpsFlowDefinition("mail(to: 'tom.abcd@jenkins.org', subject: 'subject');", true));

        WorkflowRun run = jenkinsRule.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());
        jenkinsRule.assertLogContains("Email not sent. All mandatory properties must be supplied ('subject', 'body').", run);
    }

    @Test
    public void test_missing_recipient() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "workflow");
        // leave out the subject and body
        job.setDefinition(new CpsFlowDefinition("mail(subject: 'Hello friend', body: 'Missing you!');", true));

        WorkflowRun run = jenkinsRule.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());
        jenkinsRule.assertLogContains("Email not sent. No recipients of any kind specified ('to', 'cc', 'bcc').", run);
    }

    @Test
    public void test_send() throws Exception {
        Mailbox.clearAll();

        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "workflow");
        job.setDefinition(new CpsFlowDefinition("mail(to: 'tom.abcd@jenkins.org', subject: 'Hello friend', body: 'Missing you!');", true));

        jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

        Mailbox mailbox = Mailbox.get("tom.abcd@jenkins.org");
        Assert.assertEquals(1, mailbox.getNewMessageCount());
        Message message = mailbox.get(0);
        Assert.assertEquals("Hello friend", message.getSubject());
        Assert.assertEquals("Missing you!", ((MimeMultipart)message.getContent()).getBodyPart(0).getContent().toString());

    }
}
