package org.jenkinsci.plugins.workflow.job;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
public class PersistenceProblemStep extends AbstractStepImpl {
    @DataBoundConstructor
    public PersistenceProblemStep() {
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(PersistenceProblemStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "persistenceProblem";
        }

        @Override
        public String getDisplayName() {
            return "Problematic Persistence";
        }
    }
}
