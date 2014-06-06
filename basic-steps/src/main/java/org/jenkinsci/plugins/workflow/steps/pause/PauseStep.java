package org.jenkinsci.plugins.workflow.steps.pause;

import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * {@link Step} that pauses for human input.
 *
 * @author Kohsuke Kawaguchi
 */
public class PauseStep extends AbstractStepImpl {
    private final String message;

    /**
     * Optional ID that uniquely identifies this pause from all others.
     */
    @DataBoundSetter
    private String id;

    private StepContext context;

    /**
     * Pause gets added here.
     */
    @StepContextParameter
    private transient Run run;

    @DataBoundConstructor
    public PauseStep(String message) {
        this.message = message;
    }

    public String getId() {
        if (id==null)
            id = Util.getDigestOf(message);
        return id;
    }

    @Override
    public boolean doStart(StepContext context) throws Exception {
        this.context = context;

        PauseAction a = run.getAction(PauseAction.class);
        if (a==null)
            run.addAction(a=new PauseAction());

        // record this pause
        a.add(this);

        return false;
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
