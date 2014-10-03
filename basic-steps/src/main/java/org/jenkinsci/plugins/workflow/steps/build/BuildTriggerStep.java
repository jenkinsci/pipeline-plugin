package org.jenkinsci.plugins.workflow.steps.build;

import hudson.Extension;
import hudson.model.ParameterValue;
import java.util.List;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * @author Vivek Pandey
 */
public class BuildTriggerStep extends AbstractStepImpl {

    private final String job;
    private List<ParameterValue> parameters;

    @DataBoundConstructor
    public BuildTriggerStep(String value) {
        this.job = value;
    }

    public String getValue() {
        return job;
    }

    public List<ParameterValue> getParameters() {
        return parameters;
    }

    @DataBoundSetter public void setParameters(List<ParameterValue> parameters) {
        this.parameters = parameters;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(BuildTriggerStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "build";
        }

        @Override
        public String getDisplayName() {
            return "Build a Job";
        }
    }
}
