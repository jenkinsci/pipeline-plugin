package org.jenkinsci.plugins.workflow.steps;

/**
 * {@link AbstractStepImpl} that always executes synchronously.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractSynchronousStepImpl<T> extends AbstractStepImpl {

    protected abstract T run(StepContext context) throws Exception;

    @Override
    protected final boolean doStart(StepContext context) throws Exception {
        try {
            context.onSuccess(run(context));
        } catch (Throwable t) {
            context.onFailure(t);
        }
        return true;
    }
}
