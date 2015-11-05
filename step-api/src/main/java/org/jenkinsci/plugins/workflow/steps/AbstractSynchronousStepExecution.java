package org.jenkinsci.plugins.workflow.steps;

import hudson.model.Executor;
import hudson.model.Result;

import static hudson.model.Result.ABORTED;

/**
 * {@link StepExecution} that always executes synchronously. This API should be used for short-lived tasks that
 * return almost instantly.
 *
 * To call legacy Jenkins APIs which are potentially long-running and interruptible yet offer no asynchronous mode
 * (for example because they block on a remoting call) use {@link AbstractSynchronousNonBlockingStepExecution}.
 * Also note that long-lived tasks which do not need to run within a Java method call should use the more general {@link AbstractStepExecutionImpl}
 *
 * @param <T> the type of the return value (may be {@link Void})
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractSynchronousStepExecution<T> extends AbstractStepExecutionImpl {
    private transient volatile Thread executing;

    protected AbstractSynchronousStepExecution() {
    }

    protected AbstractSynchronousStepExecution(StepContext context) {
        super(context);
    }

    /**
     * Meat of the execution.
     *
     * When this method returns, a step execution is over.
     */
    protected abstract T run() throws Exception;

    @Override
    public final boolean start() throws Exception {
        executing = Thread.currentThread();
        try {
            getContext().onSuccess(run());
        } catch (Throwable t) {
            getContext().onFailure(t);
        } finally {
            executing = null;
        }
        return true;
    }

    /**
     * If the computation is going synchronously, try to cancel that.
     */
    @Override
    public void stop(Throwable cause) throws Exception {
        Thread e = executing;   // capture
        if (e!=null) {
            if (e instanceof Executor) {
                ((Executor) e).interrupt(ABORTED, new ExceptionCause(cause));
            } else {
                e.interrupt();
            }
        }
    }
}
