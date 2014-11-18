package org.jenkinsci.plugins.workflow.steps;

import com.google.common.util.concurrent.FutureCallback;

import java.io.Serializable;

/**
 * {@link FutureCallback} enhanced to track {@link BodyExecution}.
 *
 * <p>
 * Whereas plain {@link FutureCallback} gets notified only of the outcome of a body execution,
 * this interface gets the following addition:
 *
 * <ul>
 * <li>
 * {@link #onStart()} that gets invoked at the beginning of the body execution.
 * This callback has to return synchronously. It is intended for performing log output,
 * update {@code FlowNode}, or some such decorative actions. For any asynchronous
 * computation that needs to happen prior to the body execution, the best place to
 * do that is before calling {@link StepContext#newBodyInvoker()}.
 *
 * <li>
 * {@link #setContext(StepContext)} that gets invoked by the caller of the step API
 * (such as workflow) prior to {@link #onStart()}, {@link #onSuccess(Object)}, and {@link #onFailure(Throwable)}
 * so that these callbacks can access contextual objects at the beginning/end of the body invocation.
 * </ul>
 *
 * @author Kohsuke Kawaguchi
 * @see BodyInvoker#withCallback(BodyExecutionCallback)
 */
public abstract class BodyExecutionCallback implements FutureCallback<Object>, Serializable {
    /**
     * The context is only scoped to a specific callback method, and so they
     * shouldn't be persisted.
     */
    private transient StepContext context;

    /**
     * This method will be called before {@link #onStart()}, {@link #onSuccess(Object)},
     * and {@link #onFailure(Throwable)} is invoked.
     *
     * <p>
     * The {@link StepContext} object given to the {@link #setContext(StepContext)} method
     * will behave almost identically to {@link StepContext} given to {@link Step#start(StepContext)},
     * except its {@link StepContext#get(Class)} methods may return different contextual
     * objects.
     */
    public void setContext(StepContext context) {
        this.context = context;
    }

    protected StepContext getContext() {
        return context;
    }

    /**
     * Notifies that the body execution has started.
     *
     * <p>
     * At this point {@link StepContext} gives you access to the objects that correspond
     * to the beginning of the body, as opposed to the objects that correspond to the invocation
     * of the step that invoked the body.
     *
     * <p>
     * So for example this is a good place to record any logging that's attributed to
     * the body execution, such as reporting that this is Nth retry of the body, or
     * that this is the parallel branch named 'xyz'.
     */
    public void onStart() {}

    /**
     * Notifies that the body execution has completed successfully.
     *
     * <p>
     * At this point {@link StepContext} gives you access to the objects that correspond
     * to the end of the body invocation.
     *
     * <p>
     * So for example this is a good place to record any logging that's attributed to
     * the end of the body execution.
     */
    @Override
    public abstract void onSuccess(Object result);

    /**
     * Notifies that the body execution has completed successfully.
     *
     * <p>
     * At this point {@link StepContext} gives you access to the objects that correspond
     * to the end of the body invocation.
     *
     * <p>
     * So for example this is a good place to record any logging that's attributed to
     * the end of the body execution.
     */
    @Override
    public abstract void onFailure(Throwable t);

    /**
     * Wraps an ordinary {@link FutureCallback} into {@link BodyExecutionCallback}.
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
        public void onSuccess(Object result) {
            v.onSuccess(result);
        }

        @Override
        public void onFailure(Throwable t) {
            v.onFailure(t);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;

}
