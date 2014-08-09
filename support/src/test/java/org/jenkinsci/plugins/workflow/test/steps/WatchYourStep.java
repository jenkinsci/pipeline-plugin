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

package org.jenkinsci.plugins.workflow.test.steps;

import com.google.common.base.Function;
import hudson.Extension;
import hudson.model.PeriodicWork;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;
import java.io.File;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

/**
 * Sample asynchronous step that suspends until a file of the specified name is created.
 *
 * @author Kohsuke Kawaguchi
 */
public class WatchYourStep extends AbstractStepImpl implements Serializable {
    /*package*/ final File watch;

    @DataBoundConstructor
    public WatchYourStep(File value) {
        this.watch = value;
    }

    /**
     * Checks presence of files synchronously.
     */
    public static void update() throws Exception {
        StepExecution.applyAll(Execution.class, new Function<Execution,Void>() {
            @Override public Void apply(Execution t) {
                t.check();
                return null;
            }
        }).get();
    }

    public static int getActiveCount() throws Exception {
        final AtomicInteger count = new AtomicInteger();
        StepExecution.applyAll(Execution.class, new Function<Execution,Void>() {
            @Override public Void apply(Execution t) {
                count.incrementAndGet();
                return null;
            }
        }).get();
        return count.get();
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "watch";
        }

        @Override
        public String getDisplayName() {
            return "Watch Path";
        }
    }

    public static class Execution extends StepExecution {
        
        @Inject private WatchYourStep step;

        @Override
        public boolean start() {
            return check(); // synchronous if already exists, else asynchronous
        }

        boolean check() {
            if (step.watch.exists()) {
                getContext().onSuccess(null);
                return true;
            } else {
                return false;
            }
        }

    }

    @Extension
    public static class WatchTask extends PeriodicWork {

        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.SECONDS.toMillis(1);
        }

        @Override
        protected void doRun() throws Exception {
            update();
        }
    }
}
