package org.jenkinsci.plugins.workflow;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A step that tries to inject subtypes of common types.
 *
 * @author Kohsuke Kawaguchi
 */
public class SubtypeInjectingStep extends AbstractStepImpl {
    @DataBoundConstructor
    public SubtypeInjectingStep() {
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(SubtypeInjectingStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "injectSubtypesAsContext";
        }

        @Override
        public String getDisplayName() {
            return "Inject subtypes as context (for unit test)";
        }
    }
}
