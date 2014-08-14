package org.jenkinsci.plugins.workflow;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link Step} that fails to persist. Used to test the behaviour of error reporting/recovery.
 *
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
