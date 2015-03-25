package org.jenkinsci.plugins.workflow.steps;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scoped to a single execution of {@link Step}, and provides insights into what's going on
 * asynchronously and aborting the activity if need be.
 *
 * <p>
 * {@link StepExecution}s are persisted whenever used to run an asynchronous operation.
 *
 * @author Kohsuke Kawaguchi
 * @author Jesse Glick
 * @see Step#start(StepContext)
 */
public abstract class StepExecution implements Serializable {
    
    @Inject private StepContext context;

    /**
     * Default constructor used by injection.
     * @see AbstractStepImpl#start
     */
    protected StepExecution() {
    }

    /**
     * If manually created, {@link StepContext} must be passed in.
     */
    protected StepExecution(StepContext context) {
        this.context = context;
    }

    public @Nonnull StepContext getContext() {
        if (context == null) {
            throw new IllegalStateException("you must either pass in a StepContext to the StepExecution constructor, or have the StepExecution be created automatically");
        }
        return context;
    }

    /**
     * Start execution of something and report the end result back to the given callback.
     *
     * Arguments are passed when {@linkplain StepDescriptor#newInstance(Map) instantiating steps}.
     *
     * @return
     *      true if the execution of this step has synchronously completed before this method returns.
     *      It is the callee's responsibility to set the return value via {@link StepContext#onSuccess(Object)}
     *      or {@link StepContext#onFailure(Throwable)}.
     *
     *      false if the asynchronous execution has started and that {@link StepContext}
     *      will be notified when the result comes in. (Note that the nature of asynchrony is such that it is possible
     *      for the {@link StepContext} to be already notified before this method returns.)
     * @throws Exception
     *      if any exception is thrown, {@link Step} is assumed to have completed abnormally synchronously
     *      (as if {@link StepContext#onFailure} is called and the method returned true.)
     */
    public abstract boolean start() throws Exception;

    /**
     * May be called if someone asks a running step to abort.
     *
     * Just like {@link Thread#interrupt()},
     * the step might not honor the request immediately.
     * Multiple stop requests might be sent.
     * It is always responsible for calling {@link StepContext#onSuccess(Object)} or (more likely)
     * {@link StepContext#onFailure(Throwable)} eventually,
     * whether or not it was asked to stop.
     *
     * <p>
     * In the workflow context, this method is meant to be used by {@code FlowExecution}, and not
     * to be called willy-nilly from UI or other human requests to pause. Use {@link BodyExecution#cancel(Throwable)}.
     *
     * @param cause
     *      Contextual information that lets the step know what resulted in stopping an executing step,
     *      passed in the hope that this will assist diagnostics.
     */
    public abstract void stop(@Nonnull Throwable cause) throws Exception;

    /**
     * Called when {@link StepExecution} is brought back into memory after restart.
     * Convenient for re-establishing the polling.
     */
    public void onResume() {}

    /**
     * Apply the given function to all the active running {@link StepExecution}s in the system.
     *
     * @return
     *      Future object that signals when the function application is complete.
     * @see StepExecutionIterator
     */
    public static ListenableFuture<?> applyAll(Function<StepExecution,Void> f) {
        List<ListenableFuture<?>> futures = new ArrayList<ListenableFuture<?>>();
        for (StepExecutionIterator i : StepExecutionIterator.all())
            futures.add(i.apply(f));
        return Futures.allAsList(futures);
    }

    /**
     * Applies only to the specific subtypes.
     */
    public static <T extends StepExecution> ListenableFuture<?> applyAll(final Class<T> type, final Function<T,Void> f) {
        return applyAll(new Function<StepExecution, Void>() {
            @Override
            public Void apply(StepExecution e) {
                if (type.isInstance(e))
                    f.apply(type.cast(e));
                return null;
            }
        });
    }

    private static final long serialVersionUID = 1L;
}
