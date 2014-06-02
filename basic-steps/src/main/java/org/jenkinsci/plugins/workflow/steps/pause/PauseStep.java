package org.jenkinsci.plugins.workflow.steps.pause;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
public class PauseStep extends AbstractStepImpl {
    private final String message;

    @DataBoundConstructor
    public PauseStep(String message) {
        this.message = message;
    }

    @Override
    public boolean doStart(StepContext context) throws Exception {
        return true;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        @Override
        public String getFunctionName() {
            return "input";
        }

        @Override
        public String getDisplayName() {
            return "Human Input";
        }
    }
}
