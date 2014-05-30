package org.jenkinsci.plugins.workflow.steps.pause;

import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.EchoStep;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Kohsuke Kawaguchi
 */
public class PauseStep extends Step {
    private final String message;

    @DataBoundConstructor
    public PauseStep(String message) {
        this.message = message;
    }

    @Override
    public boolean start(StepContext context) throws Exception {
        return true;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "input";
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) {
            return new PauseStep((String) arguments.get("value"));
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return Collections.<Class<?>>singleton(TaskListener.class);
        }


        @Override
        public String getDisplayName() {
            return "Human Input";
        }
    }
}
