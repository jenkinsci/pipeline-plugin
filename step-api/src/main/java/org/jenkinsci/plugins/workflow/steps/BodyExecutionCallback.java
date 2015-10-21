package org.jenkinsci.plugins.workflow.steps;

import com.google.common.util.concurrent.FutureCallback;

import java.io.Serializable;

/**
 * {@link FutureCallback} enhanced to track {@link BodyExecution}.
 *
 * <p>
 * Body execution reports the callback in the order {@link #onStart}, then either {@link #onSuccess} or {@link #onFailure}.
 *
 * @author Kohsuke Kawaguchi
 * @see BodyInvoker#withCallback(BodyExecutionCallback)
 */
public abstract class BodyExecutionCallback implements Serializable {
    /**
     * Notifies that the body execution has started.
     *
     * <p>
     * This callback has to return synchronously. It is intended for performing log output,
     * update {@code FlowNode}, or some such decorative actions. For any asynchronous
     * computation that needs to happen prior to the body execution, the best place to
     * do that is before calling {@link StepContext#newBodyInvoker()}.
     *
     * <p>
     * {@link StepContext} given to this method lets you access objects that correspond
     * to the beginning of the body, as opposed to the objects that correspond to the invocation
     * of the step that invoked the body. Otherwise the context is identical in behaviour
     * to that given to {@link Step#start(StepContext)}.
     *
     * <p>
     * So for example this is a good place to record any logging that's attributed to
     * the body execution, such as reporting that this is Nth retry of the body, or
     * that this is the parallel branch named 'xyz'.
     */
    public void onStart(StepContext context) {}

    /**
     * Notifies that the body execution has completed successfully.
     *
     * <p>
     * {@link StepContext} given to this method lets you access objects that correspond
     * to the end of the body, as opposed to the objects that correspond to the invocation
     * of the step that invoked the body. Otherwise the context is identical in behaviour
     * to that given to {@link Step#start(StepContext)}.
     *
     * <p>
     * So for example this is a good place to record any logging that's attributed to
     * the end of the body execution.
     */
    public abstract void onSuccess(StepContext context, Object result);

    /**
     * Notifies that the body execution has aborted abnormally.
     *
     * <p>
     * See {@link #onSuccess(StepContext, Object)} for the discussion of how
     * the given {@link StepContext} behaves.
     */
    public abstract void onFailure(StepContext context, Throwable t);

    /**
     * Wraps an ordinary {@link FutureCallback} into {@link BodyExecutionCallback}.
     * You lose some power this way ({@link #onStart} and per-body {@link StepContext})
     * but may be convenient if you already have a {@link FutureCallback} from some other source.
     * For example, you can wrap your own {@link StepContext} if your step is a tail call to its body.
     */
    public static BodyExecutionCallback wrap(FutureCallback<Object> v) {
        return v instanceof BodyExecutionCallback ? (BodyExecutionCallback)v : new Wrapper(v);
    }

    private static class Wrapper extends BodyExecutionCallback {
        private final FutureCallback<Object> v;

        public Wrapper(FutureCallback<Object> v) {
            if (!(v instanceof Serializable))
                throw new IllegalArgumentException(v.getClass()+" is not serializable");
            this.v = v;
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            v.onSuccess(result);
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            v.onFailure(t);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;

    /**
     * A convenience subclass for the common case that the step expects to run its block just once and return the same value (or throw the same error).
     * @see <a href="https://en.wikipedia.org/wiki/Tail_call">Tail call</a>
     */
    public static abstract class TailCall extends BodyExecutionCallback {

        private static final long serialVersionUID = 1L;

        /**
         * Called when the body is finished.
         * @param context the body context as passed to {@link #onSuccess} or {@link #onFailure}
         * @throws Exception if anything is thrown here, the step fails too
         */
        protected abstract void finished(StepContext context) throws Exception;

        @Override public final void onSuccess(StepContext context, Object result) {
            try {
                finished(context);
                context.onSuccess(result);
            } catch (Exception x) {
                context.onFailure(x);
            }
        }

        @Override public final void onFailure(StepContext context, Throwable t) {
            try {
                finished(context);
                context.onFailure(t);
            } catch (Exception x) {
                context.onFailure(x);
            }
        }

    }

}
