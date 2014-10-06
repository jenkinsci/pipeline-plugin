package org.jenkinsci.plugins.workflow.cps.steps;

import com.google.inject.Inject;
import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.FilePath;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
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
    private final String path;

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

    public static class EvaluateStepExecution extends StepExecution {
        @StepContextParameter
        private transient FilePath cwd;

        @Inject
        private EvaluateStep step;

        @Override
        public boolean start() throws Exception {
            GroovyShell shell = CpsThread.current().getExecution().getShell();

            // this might throw CpsCallableInvocation to trigger async execution
            // TODO in that case what happens to the return value?
            Object o = shell.evaluate(cwd.child(step.path).readToString());

            getContext().onSuccess(o);
            return true;
        }

        @Override
        public void stop() throws Exception {
            // TODO is there a test confirming that this gets passed on to the running step inside the evaluated script?
        }
    }

}
