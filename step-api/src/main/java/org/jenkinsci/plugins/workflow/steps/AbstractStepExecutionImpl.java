package org.jenkinsci.plugins.workflow.steps;

import com.google.inject.Inject;

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


    /**
     * Reinject {@link StepContextParameter}s.
     * The originating {@link Step} may be injected for the benefit of {@link #start}, but must be {@code transient},
     * and you must mark it {@link Inject#optional} since it will <em>not</em> be restored here.
     * If you need any information from the step definition after a restart, {@link Step#clone} it or otherwise retain fields.
     */
    @Override
    public void onResume() {
        inject();
    }

    protected void inject() {
        try {
            AbstractStepImpl.prepareInjector(getContext(), null).injectMembers(this);
        } catch (Exception e) {
            getContext().onFailure(e);
        }
    }
}
