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
public class LoadStep extends AbstractStepImpl {
    /**
     * Relative path of the script within the current workspace.
     */
    private final String path;

    @DataBoundConstructor
    public LoadStep(String path) {
        this.path = path;
    }
    
    public String getPath() {
        return path;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(LoadStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "load";
        }

        @Override
        public String getDisplayName() {
            return "Evaluate a Groovy source file into the workflow script";
        }
    }

}
