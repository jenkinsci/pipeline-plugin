package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
public class ArtifactUnarchiverStep extends AbstractStepImpl {
    /**
     * Files to copy over.
     */
    @DataBoundSetter public Map<String,String> mapping;

    // TBD: alternate single-file option value ~ Collections.singletonMap(value, value)

    @DataBoundConstructor public ArtifactUnarchiverStep() {}

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(ArtifactUnarchiverStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "unarchive";
        }

        @Override
        public String getDisplayName() {
            return "Copy archived artifacts into the workspace";
        }

        @Override public boolean isAdvanced() {
            return true;
        }

    }

}
