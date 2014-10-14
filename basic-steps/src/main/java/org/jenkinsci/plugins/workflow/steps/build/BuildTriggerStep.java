package org.jenkinsci.plugins.workflow.steps.build;

import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.model.ItemGroup;
import hudson.model.ParameterValue;
import java.util.List;
import jenkins.model.ParameterizedJobMixIn;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author Vivek Pandey
 */
public class BuildTriggerStep extends AbstractStepImpl {

    private final String value;
    private List<ParameterValue> parameters;

    @DataBoundConstructor
    public BuildTriggerStep(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
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

        public AutoCompletionCandidates doAutoCompleteValue(@AncestorInPath ItemGroup<?> context, @QueryParameter String value) {
            return AutoCompletionCandidates.ofJobNames(ParameterizedJobMixIn.ParameterizedJob.class, value, context);
        }

    }
}
