package org.jenkinsci.plugins.workflow.steps;

import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Similar to {@link AbstractSynchronousStepExecution} (it executes synchronously too) but it does not block the CPS VM thread.
 * @see StepExecution
 * @param <T> the type of the return value (may be {@link Void})
 */
public abstract class AbstractSynchronousNonBlockingStepExecution<T> extends AbstractStepExecutionImpl {

    private transient volatile Future<?> task;

    private static ExecutorService executorService;

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
        task = getExecutorService().submit(new StepRunner(this));
        return false;
    }

    /**
     * If the computation is going synchronously, try to cancel that.
     */
    @Override
    public void stop(Throwable cause) throws Exception {
        if (task != null) {
            task.cancel(true);
        }
        getContext().onFailure(cause);
    }

    @Override
    public void onResume() {
        getContext().onFailure(new Exception("Resume after a restart not supported for non-blocking synchronous steps"));
    }

    private static synchronized ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newCachedThreadPool(new NamingThreadFactory(new DaemonThreadFactory(), "org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution"));
        }
        return executorService;
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
