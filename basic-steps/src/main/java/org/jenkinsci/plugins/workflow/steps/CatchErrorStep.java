/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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
import hudson.AbortException;
import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Runs a block.
 * If it fails, marks the build as failed, but continues execution.
 */
public final class CatchErrorStep extends AbstractStepImpl {

    @DataBoundConstructor public CatchErrorStep() {}

    @Extension public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "catchError";
        }

        @Override public String getDisplayName() {
            return "Catch Error and Continue";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

    }

    public static final class Execution extends AbstractStepExecutionImpl {

        /** TODO unused since it is transient, just as a marker that we will need it later: https://trello.com/c/cyXGkuVv/91-stepcontextparameter-should-be-reinjected-after-restart */
        @StepContextParameter private transient Run<?,?> run;
        @StepContextParameter private transient TaskListener listener;

        @Override public boolean start() throws Exception {
            StepContext context = getContext();
            context.newBodyInvoker().withCallback(new Callback(context)).start();
            return false;
        }

        @Override public void stop(Throwable cause) throws Exception {
            // nothing to do
        }

        private static final class Callback implements FutureCallback<Object>, Serializable {

            private final StepContext context;

            Callback(StepContext context) {
                this.context = context;
            }

            @Override public void onSuccess(Object result) {
                try {
                    context.get(Run.class).setResult(Result.SUCCESS);
                } catch (Exception x) {
                    context.onFailure(x);
                    return;
                }
                context.onSuccess(null); // we do not pass up a result, since onFailure cannot
            }

            @Override public void onFailure(Throwable t) {
                try {
                    // TODO as in RetryStep, we cannot actually print the error message here
                    TaskListener listener = context.get(TaskListener.class);
                    if (t instanceof AbortException) {
                        listener.error(t.getMessage());
                    } else {
                        t.printStackTrace(listener.getLogger());
                    }
                    context.get(Run.class).setResult(Result.FAILURE);
                    context.onSuccess(null);
                } catch (Exception x) {
                    context.onFailure(x);
                }
            }

        }

        private static final long serialVersionUID = 1L;

    }

}
