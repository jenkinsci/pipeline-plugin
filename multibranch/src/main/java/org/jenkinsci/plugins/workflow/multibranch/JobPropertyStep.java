/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.multibranch;

import hudson.AbortException;
import hudson.BulkChange;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.Run;
import java.util.List;
import javax.inject.Inject;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Resets the properties of the current job.
 */
public class JobPropertyStep extends AbstractStepImpl {

    private final List<JobProperty<?>> properties;

    @DataBoundConstructor public JobPropertyStep(List<JobProperty<?>> properties) {
        this.properties = properties;
    }

    public List<JobProperty<?>> getProperties() {
        return properties;
    }

    public static class Execution extends AbstractSynchronousStepExecution<Void> {

        @Inject transient JobPropertyStep step;
        @StepContextParameter Run<?,?> build;

        @SuppressWarnings({"unchecked", "rawtypes"}) // untypable
        @Override protected Void run() throws Exception {
            Job<?,?> job = build.getParent();
            for (JobProperty prop : step.properties) {
                if (!prop.getDescriptor().isApplicable(job.getClass())) {
                    throw new AbortException("cannot apply " + prop.getDescriptor().getId() + " to a " + job.getClass().getSimpleName());
                }
            }
            BulkChange bc = new BulkChange(job);
            try {
                for (JobProperty prop : job.getAllProperties()) {
                    if (prop instanceof BranchJobProperty) {
                        // TODO do we need to define an API for other properties which should not be removed?
                        continue;
                    }
                    job.removeProperty(prop);
                }
                for (JobProperty prop : step.properties) {
                    job.addProperty(prop);
                }
                bc.commit();
            } finally {
                bc.abort();
            }
            return null;
        }

    }

    @Extension public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "properties";
        }

        @Override public String getDisplayName() {
            return "Set job properties";
        }

    }

}
