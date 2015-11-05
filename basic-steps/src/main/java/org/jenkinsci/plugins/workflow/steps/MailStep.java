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

import com.google.inject.Inject;

import hudson.AbortException;
import hudson.Extension;
import hudson.model.TaskListener;
import jenkins.plugins.mailer.tasks.MimeMessageBuilder;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;

import java.io.UnsupportedEncodingException;

/**
 * Simple email sender step.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class MailStep extends AbstractStepImpl {

    @DataBoundSetter
    public String charset;

    public final String subject;

    public final String body;

    @DataBoundSetter
    public String from;

    @DataBoundSetter
    public String to;

    @DataBoundSetter
    public String cc;

    @DataBoundSetter
    public String bcc;

    @DataBoundSetter
    public String replyTo;

    @DataBoundSetter
    public String mimeType;

    @DataBoundConstructor
    public MailStep(@Nonnull String subject, @Nonnull String body) {
        this.subject = subject;
        this.body = body;
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(MailStepExecution.class);
        }

        @Override public String getFunctionName() {
            return "mail";
        }

        @Override public String getDisplayName() {
            return "Mail";
        }
    }

    /**
     * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
     */
    public static class MailStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;

        @Inject
        private transient MailStep step;

        @StepContextParameter
        private transient TaskListener listener;

        @Override
        protected Void run() throws Exception {
            MimeMessage mimeMessage = buildMimeMessage();
            Transport.send(mimeMessage);
            return null;
        }

        private MimeMessage buildMimeMessage() throws UnsupportedEncodingException, MessagingException, AbortException {
            if (StringUtils.isBlank(step.subject) || StringUtils.isBlank(step.body)) {
                throw new AbortException("Email not sent. All mandatory properties must be supplied ('subject', 'body').");
            }

            MimeMessageBuilder messageBuilder = new MimeMessageBuilder().setListener(listener);

            if (step.subject != null) {
                messageBuilder.setSubject(step.subject);
            }
            if (step.body != null) {
                messageBuilder.setBody(step.body);
            }
            if (step.from != null) {
                messageBuilder.setFrom(step.from);
            }
            if (step.replyTo != null) {
                messageBuilder.setReplyTo(step.replyTo);
            }
            if (step.to != null) {
                messageBuilder.addRecipients(step.to, Message.RecipientType.TO);
            }
            if (step.cc != null) {
                messageBuilder.addRecipients(step.cc, Message.RecipientType.CC);
            }
            if (step.bcc != null) {
                messageBuilder.addRecipients(step.bcc, Message.RecipientType.BCC);
            }
            if (step.charset != null) {
                messageBuilder.setCharset(step.charset);
            }
            if (step.mimeType != null) {
                messageBuilder.setMimeType(step.mimeType);
            }

            MimeMessage message = messageBuilder.buildMimeMessage();

            Address[] allRecipients = message.getAllRecipients();
            if (allRecipients == null || allRecipients.length == 0) {
                throw new AbortException("Email not sent. No recipients of any kind specified ('to', 'cc', 'bcc').");
            }

            return message;
        }
    }
}
