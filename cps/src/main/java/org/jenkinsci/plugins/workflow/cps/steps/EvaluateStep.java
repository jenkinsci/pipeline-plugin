package org.jenkinsci.plugins.workflow.cps.steps;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Evaluate arbitrary script file.
 *
 * @author Kohsuke Kawaguchi
 */
public class EvaluateStep extends AbstractStepImpl {
    /**
     * Relative path of the script within the current workspace.
     */
    /*package*/ final String path;

    @DataBoundConstructor
    public EvaluateStep(String value) {
        this.path = value;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(EvaluateStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "evaluateWorkspaceScript";
        }

        @Override
        public String getDisplayName() {
            return "Evaluate a Groovy source file into the workflow script";
        }
    }
}
