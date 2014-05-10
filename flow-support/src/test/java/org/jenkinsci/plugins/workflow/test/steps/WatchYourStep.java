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

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import hudson.Extension;
import hudson.model.PeriodicWork;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Sample asynchronous step that suspends until a file of the specified name is created.
 *
 * @author Kohsuke Kawaguchi
 */
public class WatchYourStep extends Step {
    private final File watch;

    @DataBoundConstructor
    public WatchYourStep(File watch) {
        this.watch = watch;
    }

    @Override
    public boolean start(StepContext context) {
        if (watch.exists()) {
            // synchronous case. Sometimes async steps can complete synchronously
            context.onSuccess(null);
            return true;
        }

        // asynchronous case.
        getDescriptor().addWatch(new Tag(watch,context));
        return false;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    private static class Tag {
        private final File path;
        private final StepContext context;

        private Tag(File path, StepContext context) {
            this.path = path;
            this.context = context;
        }
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        private List<Tag> activeWatches = new ArrayList<Tag>();

        public DescriptorImpl() {
            load();
        }

        /*package*/ synchronized void addWatch(Tag t) {
            activeWatches.add(t);
            save();
        }

        public synchronized void watchUpdate() {
            boolean changed = false;
            for (Iterator<Tag> itr = activeWatches.iterator(); itr.hasNext(); ) {
                Tag t = itr.next();
                if (t.path.exists()) {
                    t.context.onSuccess(null);
                    itr.remove();
                    changed = true;
                }
            }
            if (changed)
                save();
        }

        public synchronized List<Tag> getActiveWatches() {
            return new ArrayList<Tag>(activeWatches);
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return Collections.emptySet();
        }

        @Override
        public String getFunctionName() {
            return "watch";
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) {
            return new WatchYourStep((File)arguments.values().iterator().next());
        }

        @Override
        public String getDisplayName() {
            return "Watch Path";
        }
    }

    @Extension
    public static class WatchTask extends PeriodicWork {
        @Inject
        DescriptorImpl d;

        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.SECONDS.toMillis(1);
        }

        @Override
        protected void doRun() throws Exception {
            d.watchUpdate();
        }
    }
}
