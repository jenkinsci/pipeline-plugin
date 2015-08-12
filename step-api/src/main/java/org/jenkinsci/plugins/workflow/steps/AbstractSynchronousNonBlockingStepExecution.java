package org.jenkinsci.plugins.workflow.steps;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jenkins.util.Timer;

/**
 * {@link StepExecution} that always executes synchronously and does not block the CPS VM thread.
 * @param <T> the type of the return value (may be {@link Void})
 */
public abstract class AbstractSynchronousNonBlockingStepExecution<T> extends AbstractStepExecutionImpl {

    private transient volatile ScheduledFuture<?> task;

    protected AbstractSynchronousNonBlockingStepExecution() {
    }

    protected AbstractSynchronousNonBlockingStepExecution(StepContext context) {
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
        task = Timer.get().schedule(new StepRunner(this), 0, TimeUnit.MILLISECONDS);
        return false;
    }

    /**
     * If the computation is going synchronously, try to cancel that.
     */
    @Override
    public void stop(Throwable cause) throws Exception {
        if (task != null) {
            task.cancel(false);
        }
        getContext().onFailure(cause);
    }

    @Override
    public void onResume() {
        getContext().onFailure(new AssertionError("Resume after a restart not supported for non-blockin synchronous steps"));
    }

    private static class StepRunner implements Runnable {

        private final AbstractSynchronousNonBlockingStepExecution<?> step;

        public StepRunner(AbstractSynchronousNonBlockingStepExecution<?> step) {
            this.step = step;
        }

        @Override
        public void run() {
            try {
                step.getContext().onSuccess(step.run());
            } catch (Exception e) {
                step.getContext().onFailure(e);
            }
        }
    }
}
