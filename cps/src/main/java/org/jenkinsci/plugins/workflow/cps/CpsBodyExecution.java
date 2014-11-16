package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Outcome;
import com.google.common.util.concurrent.FutureCallback;
import hudson.model.Result;
import jenkins.model.CauseOfInterruption;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import javax.annotation.concurrent.GuardedBy;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link BodyExecution} impl for CPS.
 *
 * This object is serializable while {@link CpsBodyInvoker} isn't.
 *
 * When the body finishes execution, this object should be notified as {@link FutureCallback}.
 *
 * @author Kohsuke Kawaguchi
 * @see CpsBodyInvoker#bodyExecution
 */
class CpsBodyExecution extends BodyExecution implements FutureCallback {
    /**
     * Thread that's executing
     */
    @GuardedBy("this")
    private CpsThread thread;

    /**
     * Set to non-null if the body execution is stopped.
     */
    @GuardedBy("this")
    private FlowInterruptedException stopped;

    @GuardedBy("this")
    private List<FutureCallback<Object>> callbacks = new ArrayList<FutureCallback<Object>>();

    @GuardedBy("this")
    private Outcome outcome;

    @Override
    public synchronized Collection<StepExecution> getCurrentExecutions() {
        if (thread==null)   return Collections.emptyList();

        StepExecution s = thread.getStep();
        if (s!=null)        return Collections.singleton(s);
        else                return Collections.emptyList();
    }

    @Override
    public boolean cancel(final CauseOfInterruption... causes) {
        // 'stopped' and 'thread' are updated atomically
        final CpsThread t;
        synchronized (this) {
            if (isDone())  return false;   // already complete
            stopped = new FlowInterruptedException(Result.ABORTED, causes); // TODO: the fact that I'm hard-coding exception seems to indicate an abstraction leak. Come back and think about this.
            t = this.thread;
        }

        if (t!=null) {
            t.getExecution().runInCpsVmThread(new FutureCallback<CpsThreadGroup>() {
                @Override
                public void onSuccess(CpsThreadGroup g) {
                    StepExecution s = t.getStep();  // this is the part that should run in CpsVmThread
                    if (s == null) {
                        // TODO: if it's not running inside a StepExecution, we need to set an interrupt flag
                        // and interrupt at an earliest convenience
                        return;
                    }

                    try {
                        s.stop(stopped);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to stop " + s, e);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    // couldn't cancel
                }
            });
        } else {
            // if it hasn't begun executing, we'll stop it when
            // it begins.
        }
        return true;
    }

    @Override
    public synchronized boolean isCancelled() {
        return stopped!=null && isDone();
    }

    @Override
    public synchronized Object get() throws InterruptedException, ExecutionException {
        while (outcome==null) {
            wait();
        }
        if (outcome.isSuccess())    return outcome.getNormal();
        else    throw new ExecutionException(outcome.getAbnormal());
    }

    @Override
    public synchronized Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long endTime = System.currentTimeMillis() + unit.toMillis(timeout);
        long remaining;
        while (outcome==null && (remaining=endTime-System.currentTimeMillis()) > 0) {
            wait(remaining);
        }

        if (outcome==null)
            throw new TimeoutException();

        if (outcome.isSuccess())    return outcome.getNormal();
        else    throw new ExecutionException(outcome.getAbnormal());
    }

    /**
     * Start running the new thread unless the stop is requested, in which case the thread gets aborted right away.
     */
    @CpsVmThreadOnly
    /*package*/ synchronized void startExecution(CpsThread t) {
        // either get the new thread going normally, or abort from the beginning
        t.resume(new Outcome(null, stopped));

        assert this.thread==null;
        this.thread = t;

    }

    public void prependCallback(FutureCallback<Object> callback) {
        if (!(callback instanceof Serializable))
            throw new IllegalStateException("Callback must be persistable, but got "+callback.getClass());

        Outcome o;
        synchronized (this) {
            if (callbacks != null) {
                callbacks.add(0,callback);
                return;
            }
            o = outcome;
        }

        // if the computation has completed,
        fire(callback, o);
    }

    public void addCallback(FutureCallback<Object> callback) {
        if (!(callback instanceof Serializable))
            throw new IllegalStateException("Callback must be persistable, but got "+callback.getClass());

        Outcome o;
        synchronized (this) {
            if (callbacks != null) {
                callbacks.add(callback);
                return;
            }
            o = outcome;
        }

        // if the computation has completed,
        fire(callback, o);
    }

    private void fire(FutureCallback<Object> callback, Outcome o) {
        if (o.isSuccess())    callback.onSuccess(o.getNormal());
        else                  callback.onFailure(o.getAbnormal());
    }

    public synchronized boolean isDone() {
        return outcome!=null;
    }

    /**
     * Atomically commits the outcome and then grabs all the callbacks.
     */
    private synchronized List<FutureCallback<Object>> grabCallbacks(Outcome o) {
        if (this.outcome!=null)     return Collections.emptyList(); // already completed

        this.outcome = o;
        List<FutureCallback<Object>> r = callbacks;
        callbacks = null;
        return r;
    }

    @Override
    public void onSuccess(Object result) {
        for (FutureCallback<Object> c : grabCallbacks(new Outcome(result,null))) {
            c.onSuccess(result);
        }
    }

    @Override
    public void onFailure(Throwable t) {
        for (FutureCallback<Object> c : grabCallbacks(new Outcome(null,t))) {
            c.onFailure(t);
        }
    }


    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(CpsBodyExecution.class.getName());
}
