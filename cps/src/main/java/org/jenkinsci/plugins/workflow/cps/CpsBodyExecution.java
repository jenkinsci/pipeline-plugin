package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Outcome;
import com.google.common.util.concurrent.FutureCallback;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import javax.annotation.concurrent.GuardedBy;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * {@link BodyExecution} impl for CPS.
 *
 * This object is serializable while {@link BodyInvoker} isn't.
 *
 * @author Kohsuke Kawaguchi
 * @see BodyInvoker#bodyExecution
 */
class CpsBodyExecution extends BodyExecution {
    /**
     * Thread that's executing
     */
    @GuardedBy("this")
    private CpsThread thread;

    /**
     * Set to non-null if the body execution is stopped.
     */
    @GuardedBy("this")
    private InterruptedException stopped;

    /**
     * Distributes the result to a list of {@link FutureCallback}s.
     */
    final FutureCallbackBroadcast broadcast = new FutureCallbackBroadcast();

    @Override
    public synchronized Collection<StepExecution> getCurrentExecutions() {
        if (thread==null)   return Collections.emptyList();

        StepExecution s = thread.getStep();
        if (s!=null)        return Collections.singleton(s);
        else                return Collections.emptyList();
    }

    @Override
    public boolean isDone() {
        return broadcast.isDone();
    }

    @Override
    public void addCallback(FutureCallback<Object> callback) {
        broadcast.addCallback(callback);
    }

    @Override
    public void stop() throws Exception {
        // 'stopped' and 'thread' are updated atomically
        CpsThread t;
        synchronized (this) {
            stopped = new InterruptedException();
            t = this.thread;
        }

        if (t!=null) {
            // TODO: if it's not running inside a StepExecution, we need to set an interrupt flag
            // and interrupt at an earliest convenience
            StepExecution s = t.getStep();
            if (s!=null)
                s.stop();
        } else {
            // if it hasn't begun executing, we'll stop it when
            // it begins.
        }
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

    class FutureCallbackBroadcast implements FutureCallback, Serializable {
        @GuardedBy("this")
        private List<FutureCallback<Object>> callbacks = new ArrayList<FutureCallback<Object>>();

        @GuardedBy("this")
        private Outcome outcome;


        public FutureCallbackBroadcast() {
        }

        public void addCallback(FutureCallback<Object> callback) {
            if (!(callback instanceof Serializable))
                throw new IllegalStateException("Callback must be persistable, but got "+callback.getClass());

            synchronized (this) {
                if (callbacks != null) {
                    callbacks.add(callback);
                }
            }

            // if the computation has completed,
            if (outcome.isSuccess())    callback.onSuccess(outcome.getNormal());
            else                        callback.onFailure(outcome.getAbnormal());
        }
        
        public synchronized boolean isDone() {
            return outcome!=null;
        }

        /**
         * Atomically commits the outcome and then grabs all the callbacks.
         */
        private synchronized List<FutureCallback<Object>> grabCallbacks(Outcome o) {
            this.outcome = o;
            List<FutureCallback<Object>> r = callbacks;
            callbacks = null;
            if (r==null)        r = Collections.emptyList();
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
    }


    private static final long serialVersionUID = 1L;
}
