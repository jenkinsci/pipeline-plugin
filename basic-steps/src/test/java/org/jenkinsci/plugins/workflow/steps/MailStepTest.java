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

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class MailStepTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void configRoundTrip() throws Exception {
        MailStep step1 = new MailStep("subject", "body");

        step1.from = "tom.fennelly@gmail.com";
        step1.to = "tom.fennelly@gmail.com";
        step1.cc = "tom.fennelly@gmail.com";
        step1.bcc = "tom.fennelly@gmail.com";
        step1.charset = "UTF-8";
        step1.replyTo = "tom.fennelly@gmail.com";
        step1.mimeType = "text/html";
        step1.defaultSuffix = "@blah.net";

        MailStep step2 = new StepConfigTester(r).configRoundTrip(step1);
        Assert.assertEquals("subject", step2.subject);
        Assert.assertEquals("body", step2.body);
        Assert.assertEquals("tom.fennelly@gmail.com", step2.from);
        Assert.assertEquals("tom.fennelly@gmail.com", step2.to);
        Assert.assertEquals("tom.fennelly@gmail.com", step2.cc);
        Assert.assertEquals("tom.fennelly@gmail.com", step2.bcc);
        Assert.assertEquals("UTF-8", step2.charset);
        Assert.assertEquals("tom.fennelly@gmail.com", step2.replyTo);
        Assert.assertEquals("text/html", step2.mimeType);
        Assert.assertEquals("@blah.net", step2.defaultSuffix);
    }

}
