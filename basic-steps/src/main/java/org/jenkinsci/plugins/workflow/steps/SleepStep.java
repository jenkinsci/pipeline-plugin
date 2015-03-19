/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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
import hudson.Extension;
import hudson.util.ListBoxModel;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import jenkins.util.Timer;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class SleepStep extends AbstractStepImpl {

    private final int time;

    private TimeUnit unit = TimeUnit.SECONDS;

    @DataBoundConstructor public SleepStep(int time) {
        this.time = time;
    }

    @DataBoundSetter public void setUnit(TimeUnit unit) {
        this.unit = unit;
    }

    public int getTime() {
        return time;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    public static final class Execution extends AbstractStepExecutionImpl {

        private static final long serialVersionUID = 1L;

        @Inject(optional=true) private transient SleepStep step;
        private long end;
        private transient volatile ScheduledFuture<?> task;

        @Override public boolean start() throws Exception {
            long now = System.currentTimeMillis();
            end = now + step.unit.toMillis(step.time);
            setupTimer(now);
            return false;
        }

        private void setupTimer(long now) {
            if (end > now) {
                task = Timer.get().schedule(new Runnable() {
                    @Override public void run() {
                        getContext().onSuccess(null);
                    }
                }, end - now, TimeUnit.MILLISECONDS);
            } else {
                getContext().onSuccess(null);
            }
        }

        @Override public void stop(Throwable cause) throws Exception {
            if (task != null) {
                task.cancel(false);
            }
            getContext().onFailure(cause);
        }

        @Override public void onResume() {
            super.onResume();
            setupTimer(System.currentTimeMillis());
        }

    }

    @Extension public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "sleep";
        }

        @Override public String getDisplayName() {
            return "Sleep";
        }

        public ListBoxModel doFillUnitItems() {
            ListBoxModel r = new ListBoxModel();
            for (TimeUnit unit : TimeUnit.values()) {
                r.add(unit.name());
            }
            return r;
        }

    }
}
