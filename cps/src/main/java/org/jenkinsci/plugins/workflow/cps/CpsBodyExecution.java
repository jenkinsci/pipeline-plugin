package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.Outcome;
import com.google.common.util.concurrent.FutureCallback;
import hudson.model.Result;
import jenkins.model.CauseOfInterruption;
import org.jenkinsci.plugins.workflow.actions.BodyInvocationAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
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
 * @author Kohsuke Kawaguchi
 * @see CpsBodyInvoker#bodyExecution
 */
class CpsBodyExecution extends BodyExecution {
    /**
     * Thread that's executing the body.
     */
    @GuardedBy("this")
    private CpsThread thread;

    /**
     * Set to non-null if the body execution is stopped.
     */
    @GuardedBy("this")
    private FlowInterruptedException stopped;

    private List<BodyExecutionCallback> callbacks = new ArrayList<BodyExecutionCallback>();

    /**
     * Context for the step who invoked its body.
     */
    private final CpsStepContext context;

    private String startNodeId;

    final Continuation onSuccess = new SuccessAdapter();

    final Continuation onFailure = new FailureAdapter();

    @GuardedBy("this")
    private Outcome outcome;

    public CpsBodyExecution(CpsStepContext context) {
        this.context = context;
    }

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

    public void prependCallback(BodyExecutionCallback callback) {
        assert !isDone();   // can be only called before it launches
        callbacks.add(0, callback);
    }

    public void addCallback(BodyExecutionCallback callback) {
        assert !isDone();   // can be only called before it launches
        callbacks.add(callback);
    }

    public boolean isDone() {
        return outcome!=null;
    }

    /*package*/ void fireOnStart(StepStartNode sn) {
        CpsBodySubContext sc = new CpsBodySubContext(context,sn);
        for (BodyExecutionCallback c : callbacks) {
            c.setContext(sc);
            c.onStart();
        }
    }

    private class FailureAdapter implements Continuation {
        @Override
        public Next receive(Object o) {
            StepEndNode en = addBodyEndFlowNode();

            Throwable t = (Throwable)o;
            en.addAction(new ErrorAction(t));

            outcome = new Outcome(null,t);
            CpsBodySubContext sc = new CpsBodySubContext(context,en);
            for (BodyExecutionCallback c : callbacks) {
                c.setContext(sc);
                c.onFailure(t);
            }

            return Next.terminate(null);
        }

        private static final long serialVersionUID = 1L;
    }

    private class SuccessAdapter implements Continuation {
        @Override
        public Next receive(Object o) {
            StepEndNode en = addBodyEndFlowNode();

            outcome = new Outcome(o,null);
            CpsBodySubContext sc = new CpsBodySubContext(context,en);
            for (BodyExecutionCallback c : callbacks) {
                c.setContext(sc);
                c.onSuccess(o);
            }

            return Next.terminate(null);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Inserts the flow node that indicates the beginning of the body invocation.
     *
     * @see CpsBodyInvoker#addBodyStartFlowNode(FlowHead)
     */
    private StepEndNode addBodyEndFlowNode() {
        try {
            FlowHead head = CpsThread.current().head;

            StepEndNode end = new StepEndNode(head.getExecution(),
                    getBodyStartNode(), head.get());
            end.addAction(new BodyInvocationAction());
            head.setNewHead(end);

            return end;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to grow the flow graph", e);
            throw new Error(e);
        }
    }

    public StepStartNode getBodyStartNode() throws IOException {
        return (StepStartNode) thread.getExecution().getNode(startNodeId);
    }


    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(CpsBodyExecution.class.getName());
}
