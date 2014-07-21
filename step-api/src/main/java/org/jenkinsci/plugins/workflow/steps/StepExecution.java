package org.jenkinsci.plugins.workflow.steps;

import javax.inject.Inject;
import java.util.Map;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 * @author Jesse Glick
 * @see Step#start(StepContext)
 */
public abstract class StepExecution {
    @Inject
    protected StepContext context;

    protected StepExecution() {
    }

    /**
     * If manually created, {@link StepContext} must be passed in.
     */
    protected StepExecution(StepContext context) {
        this.context = context;
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
     *      (as if {@link StepContext#onFailure(Throwable) is called and the method returned true.)
     */
    public abstract boolean start() throws Exception;

    // TODO: add methods like abort()
    /**
     * May be called if someone asks a running step to abort.
     * The step might not honor the request (the default implementation does nothing),
     * or it might do so but not immediately.
     * Multiple stop requests might be sent.
     * It is always responsible for calling {@link StepContext#onSuccess(Object)} or (more likely)
     * {@link StepContext#onFailure(Throwable)} eventually,
     * whether or not it was asked to stop.
     */
    public void stop() {}

}
