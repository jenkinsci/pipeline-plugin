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

import com.google.common.base.Function;
import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import jenkins.util.Timer;
import org.kohsuke.stapler.DataBoundConstructor;

public final class WaitForConditionStep extends AbstractStepImpl {

    @DataBoundConstructor public WaitForConditionStep() {}

    public static final class Execution extends AbstractStepExecutionImpl {

        private static final long serialVersionUID = 1;
        /** Unused, just to force the descriptor to request it. */
        @StepContextParameter private transient TaskListener listener;
        private volatile BodyExecution body;
        private transient volatile ScheduledFuture<?> task;
        /**
         * TODO JENKINS-26148 is there no cleaner way of finding the StepExecution that created a BodyExecutionCallback?
         * @see #retry(String, StepContext)
         */
        private final String id = UUID.randomUUID().toString();
        private static final float RECURRENCE_PERIOD_BACKOFF = 1.2f;
        static final long MIN_RECURRENCE_PERIOD = 250; // ¼s
        // Do we want a maximum, or can it grow to any size?
        long recurrencePeriod = MIN_RECURRENCE_PERIOD;

        @Override public boolean start() throws Exception {
            body = getContext().newBodyInvoker().withCallback(new Callback(id)).start();
            return false;
        }

        @Override public void stop(Throwable cause) throws Exception {
            if (body != null) {
                body.cancel(cause);
            }
            if (task != null) {
                task.cancel(false);
                getContext().onFailure(cause);
            }
        }

        @Override public void onResume() {
            super.onResume();
            recurrencePeriod = MIN_RECURRENCE_PERIOD;
            if (body == null) {
                // Restarted while waiting for the timer to go off. Rerun now.
                body = getContext().newBodyInvoker().withCallback(new Callback(id)).start();
            } // otherwise we are in the middle of the body already, so let it run
        }

        private static void retry(final String id, final StepContext context) {
            StepExecution.applyAll(Execution.class, new Function<Execution, Void>() {
                @Override public Void apply(@Nonnull Execution execution) {
                    if (execution.id.equals(id)) {
                        execution.retry(context);
                    }
                    return null;
                }
            });
        }

        private void retry(StepContext perBodyContext) {
            body = null;
            getContext().saveState();
            try {
                perBodyContext.get(TaskListener.class).getLogger().println("Will try again after " + Util.getTimeSpanString(recurrencePeriod));
            } catch (Exception x) {
                getContext().onFailure(x);
                return;
            }
            task = Timer.get().schedule(new Runnable() {
                @Override public void run() {
                    task = null;
                    body = getContext().newBodyInvoker().withCallback(new Callback(id)).start();
                }
            }, recurrencePeriod, TimeUnit.MILLISECONDS);
            recurrencePeriod *= RECURRENCE_PERIOD_BACKOFF;
        }

    }

    private static final class Callback extends BodyExecutionCallback {

        private static final long serialVersionUID = 1;
        private final String id;

        Callback(String id) {
            this.id = id;
        }

        @Override public void onSuccess(final StepContext context, Object result) {
            if (!(result instanceof Boolean)) {
                context.onFailure(new ClassCastException("body return value " + result + " is not boolean"));
                return;
            }
            if ((Boolean) result) {
                context.onSuccess(null);
                return;
            }
            Execution.retry(id, context);
        }

        @Override public void onFailure(StepContext context, Throwable t) {
            context.onFailure(t);
        }

    }

    @Extension public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "waitUntil";
        }

        @Override public String getDisplayName() {
            return "Wait for condition";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

    }

}
