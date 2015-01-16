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
package org.jenkinsci.plugins.workflow.support.steps.mail;

import com.google.inject.Inject;
import hudson.model.TaskListener;
import jenkins.plugins.mailer.tasks.MimeMessageBuilder;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.annotation.Nullable;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class MailStepExecution extends AbstractStepExecutionImpl {

    public static final long serialVersionUID = 1L;

    @Inject
    private transient MailStep step;

    @StepContextParameter
    private transient TaskListener listener;

    @Override
    public boolean start() throws Exception {
        if (StringUtils.isBlank(step.to) || StringUtils.isBlank(step.subject) || StringUtils.isBlank(step.body)) {
            String errorMessage = "Email not sent. All mandatory properties must be supplied ('to', 'subject', 'body').";
            if (step.continueOnError) {
                listener.error(errorMessage);
                getContext().onSuccess(null);
                return true;
            } else {
                throw new IllegalArgumentException(errorMessage);
            }
        }

        MimeMessage mimeMessage = null;
        try {
            mimeMessage = buildMimeMessage();
            Transport.send(mimeMessage);
            getContext().onSuccess(null);
        } catch (Exception e) {
            if (step.continueOnError) {
                throw e;
            } else {
                listener.error("Failed to send email.", e);
                getContext().onSuccess(null);
            }
        }

        // Sync exec.
        // TODO: See about doing it async ?
        return true;
    }

    @Override
    public void stop(@Nullable Throwable cause) throws Exception {
    }

    private MimeMessage buildMimeMessage() throws UnsupportedEncodingException, MessagingException {
        return new MimeMessageBuilder()
                .setListener(listener)
                .setSubject(step.subject)
                .setBody(step.body)
                .setCharset(step.charset)
                .setMimeType(step.mimeType)
                .setFrom(step.from)
                .addRecipients(step.to, Message.RecipientType.TO)
                .addRecipients(step.cc, Message.RecipientType.CC)
                .addRecipients(step.bcc, Message.RecipientType.BCC)
                .setReplyTo(step.replyTo)
                .setDefaultSuffix(step.defaultSuffix)
                .buildMimeMessage();
    }
}
