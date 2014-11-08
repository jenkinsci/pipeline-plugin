package org.jenkinsci.plugins.workflow.steps;

/**
 * Partial implementation of {@link StepExecution} that injects {@link StepContextParameter} upon resume.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractStepExecutionImpl extends StepExecution {

    protected AbstractStepExecutionImpl() {
    }

    protected AbstractStepExecutionImpl(StepContext context) {
        super(context);
    }

    @Override
    public AbstractStepDescriptorImpl getStepDescriptor() {
        for (StepDescriptor sd : StepDescriptor.all()) {
            if (sd instanceof AbstractStepDescriptorImpl) {
                AbstractStepDescriptorImpl asd = (AbstractStepDescriptorImpl) sd;
                if (asd.getExecutionType()==getClass())
                    return asd;
            }
        }
        throw new IllegalStateException("No descriptor found for "+getClass());
    }

    /**
     * Reinject context parameters.
     */
    @Override
    public void onResume() {
        try {
            getStepDescriptor().prepareInjector(getContext(),null).injectMembers(this);
        } catch (Exception e) {
            getContext().onFailure(e);
        }
    }
}
