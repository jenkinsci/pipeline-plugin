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

import hudson.Extension;
import hudson.model.TaskListener;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * A simple echo back statement.
 *
 * @author Kohsuke Kawaguchi
 */
public class EchoStep extends AbstractStepImpl {
    private final String message;

    @StepContextParameter
    private transient TaskListener listener;

    @DataBoundConstructor
    public EchoStep(String value) {
        this.message = value;
    }

    @Override
    public boolean doStart(StepContext context) throws Exception {
        listener.getLogger().println(message);
        context.onSuccess(null);
        return true;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        @Override
        public String getFunctionName() {
            return "echo";
        }

        @Override
        public String getDisplayName() {
            return "Print Message";
        }
    }
}
