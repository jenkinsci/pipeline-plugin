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

import com.google.inject.Inject;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A step that runs a {@link SimpleBuildStep} as defined in Jenkins core.
 */
public final class CoreStep extends AbstractStepImpl {

    public final SimpleBuildStep delegate;

    @DataBoundConstructor public CoreStep(SimpleBuildStep delegate) {
        this.delegate = delegate;
    }

    private static final class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        @Inject private transient CoreStep step;
        @StepContextParameter private transient Run<?,?> run;
        @StepContextParameter private transient FilePath workspace;
        @StepContextParameter private transient Launcher launcher;
        @StepContextParameter private transient TaskListener listener;

        @Override protected Void run() throws Exception {
            step.delegate.perform(run, workspace, launcher, listener);
            return null;
        }

        private static final long serialVersionUID = 1L;

    }

    @Extension public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "step";
        }

        @Override public String getDisplayName() {
            return "General Build Step";
        }

        public Collection<? extends Descriptor<?>> getApplicableDescriptors() {
            // Jenkins.instance.getDescriptorList(SimpleBuildStep) is empty, presumably because that itself is not a Describable.
            List<Descriptor<?>> r = new ArrayList<Descriptor<?>>();
            populate(r, Builder.class);
            populate(r, Publisher.class);
            return r;
        }
        private <T extends Describable<T>,D extends Descriptor<T>> void populate(List<Descriptor<?>> r, Class<T> c) {
            Jenkins j = Jenkins.getInstance();
            if (j == null) {
                return;
            }
            for (Descriptor<?> d : j.getDescriptorList(c)) {
                if (SimpleBuildStep.class.isAssignableFrom(d.clazz)) {
                    r.add(d);
                }
            }
        }

    }

}
