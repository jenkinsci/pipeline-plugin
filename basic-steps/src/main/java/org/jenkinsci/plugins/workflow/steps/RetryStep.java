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

import com.google.common.util.concurrent.FutureCallback;
import hudson.Extension;
import hudson.model.TaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Executes the body up to N times.
 *
 * @author Kohsuke Kawaguchi
 */
public class RetryStep extends AbstractStepImpl implements Serializable {
    private final int count;

    @DataBoundConstructor
    public RetryStep(int value) {
        this.count = value;
    }

    @Override
    public boolean doStart(StepContext context) {
        context.invokeBodyLater(new Callback(context));
        return false;   // execution is asynchronous
    }

    private class Callback implements FutureCallback, Serializable {
        private final StepContext context;
        private int left;

        public Callback(StepContext context) {
            this.context = context;
            left = count;
        }

        @Override
        public void onSuccess(Object result) {
            context.onSuccess(result);
        }

        @Override
        public void onFailure(Throwable t) {
            try {
                // TODO: here we want to access TaskListener that belongs to the body invocation end node.
                // how should we do that?
                /* TODO not currently legal:
                TaskListener l = context.get(TaskListener.class);
                t.printStackTrace(l.error("Execution failed"));
                */
                left--;
                if (left>0) {
                    /*
                    l.getLogger().println("Retrying");
                    */
                    context.invokeBodyLater(this);
                } else {
                    context.onFailure(t);
                }
            } catch (Throwable p) {
                context.onFailure(p);
            }
        }

        private static final long serialVersionUID = 1L;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        @Override
        public String getFunctionName() {
            return "retry";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Retry the body up to N times";
        }
    }

    private static final long serialVersionUID = 1L;
}
