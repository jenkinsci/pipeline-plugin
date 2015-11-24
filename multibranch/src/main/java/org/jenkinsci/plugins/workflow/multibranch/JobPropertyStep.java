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
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Run;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import jenkins.branch.BuildRetentionBranchProperty;
import jenkins.branch.RateLimitBranchProperty;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Resets the properties of the current job.
 */
@SuppressWarnings("rawtypes") // TODO JENKINS-26535: cannot bind List<JobProperty<?>>
public class JobPropertyStep extends AbstractStepImpl {

    private final List<JobProperty> properties;

    @DataBoundConstructor public JobPropertyStep(List<JobProperty> properties) {
        this.properties = properties;
    }

    public List<JobProperty> getProperties() {
        return properties;
    }

    public Map<JobPropertyDescriptor,JobProperty> getPropertiesMap() {
        return Descriptor.toMap(properties);
    }

    public static class Execution extends AbstractSynchronousStepExecution<Void> {

        @Inject transient JobPropertyStep step;
        @StepContextParameter transient Run<?,?> build;

        @SuppressWarnings("unchecked") // untypable
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

        private static final long serialVersionUID = 1L;

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

        @Override public Step newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            // A modified version of RequestImpl.TypePair.convertJSON.
            // Works around the fact that Stapler does not call back into Descriptor.newInstance for nested objects.
            List<JobProperty> properties = new ArrayList<JobProperty>();
            ClassLoader cl = req.getStapler().getWebApp().getClassLoader();
            @SuppressWarnings("unchecked") Set<Map.Entry<String,Object>> entrySet = formData.getJSONObject("propertiesMap").entrySet();
            for (Map.Entry<String,Object> e : entrySet) {
                if (e.getValue() instanceof JSONObject) {
                    String className = e.getKey().replace('-', '.'); // decode JSON-safe class name escaping
                    Class<? extends JobProperty> itemType;
                    try {
                        itemType = cl.loadClass(className).asSubclass(JobProperty.class);
                    } catch (ClassNotFoundException x) {
                        throw new FormException(x, "propertiesMap");
                    }
                    JobPropertyDescriptor d = (JobPropertyDescriptor) Jenkins.getActiveInstance().getDescriptorOrDie(itemType);
                    JSONObject more = (JSONObject) e.getValue();
                    JobProperty property = d.newInstance(req, more);
                    if (property != null) {
                        properties.add(property);
                    }
                }
            }
            return new JobPropertyStep(properties);
        }

        @Restricted(DoNotUse.class) // f:repeatableHeteroProperty
        public Collection<? extends Descriptor<?>> getPropertyDescriptors() {
            List<Descriptor<?>> result = new ArrayList<Descriptor<?>>();
            for (JobPropertyDescriptor p : ExtensionList.lookup(JobPropertyDescriptor.class)) {
                if (p.isApplicable(WorkflowJob.class)) {
                    result.add(p);
                }
            }
            return result;
        }

    }

    @Extension public static class HideSuperfluousBranchProperties extends DescriptorVisibilityFilter {

        @Override public boolean filter(Object context, Descriptor descriptor) {
            if (context instanceof WorkflowMultiBranchProject && (descriptor.clazz == RateLimitBranchProperty.class || descriptor.clazz == BuildRetentionBranchProperty.class)) {
                // These are both adequately handled by declarative job properties.
                return false;
            }
            return true;
        }

    }

}
