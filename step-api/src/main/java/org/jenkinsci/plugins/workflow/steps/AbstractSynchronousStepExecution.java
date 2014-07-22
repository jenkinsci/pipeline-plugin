package org.jenkinsci.plugins.workflow.steps;

/**
 * {@link StepExecution} that always executes synchronously.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractSynchronousStepExecution<T> extends StepExecution {
    private transient volatile Thread executing;

    protected AbstractSynchronousStepExecution() {
    }

    protected AbstractSynchronousStepExecution(StepContext context) {
        super(context);
    }

    protected abstract T run() throws Exception;

    @Override
    public boolean start() throws Exception {
        executing = Thread.currentThread();
        try {
            context.onSuccess(run());
        } catch (Throwable t) {
            context.onFailure(t);
        } finally {
            executing = null;
        }
        return true;
    }

    /**
     * If the computation is going synchronously, try to cancel that.
     */
    @Override
    public void stop() throws Exception {
        Thread e = executing;   // capture
        if (e!=null)
            e.interrupt();
    }
}
