package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.Outcome;
import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import com.cloudbees.groovy.cps.impl.FunctionCallEnv;
import com.cloudbees.groovy.cps.impl.SourceLocation;
import com.cloudbees.groovy.cps.impl.TryBlockEnv;
import com.cloudbees.groovy.cps.sandbox.SandboxInvoker;
import com.google.common.util.concurrent.FutureCallback;
import hudson.model.Action;
import hudson.model.Result;
import jenkins.model.CauseOfInterruption;
import org.jenkinsci.plugins.workflow.actions.BodyInvocationAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.PROGRAM;

/**
 * {@link BodyExecution} impl for CPS.
 *
 * Instantiated when {@linkplain CpsBodyInvoker#start() the execution is scheduled},
 * and {@link CpsThreadGroup} gets updated with the new thread in the {@link #launch(CpsBodyInvoker, CpsThread, FlowHead)}
 * method, and this is the point in which the actual execution gest under way.
 *
 * <p>
 * This object is serializable while {@link CpsBodyInvoker} isn't.
 *
 * @author Kohsuke Kawaguchi
 * @see CpsBodyInvoker#start()
 */
@PersistIn(PROGRAM)
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

    private final List<BodyExecutionCallback> callbacks;

    /**
     * Context for the step who invoked its body.
     */
    private final CpsStepContext context;

    private String startNodeId;

    final Continuation onSuccess = new SuccessAdapter();

    final Continuation onFailure = new FailureAdapter();

    @GuardedBy("this")
    private Outcome outcome;

    public CpsBodyExecution(CpsStepContext context, List<BodyExecutionCallback> callbacks) {
        this.context = context;
        this.callbacks = callbacks;
    }

    /**
     * Starts evaluating the body.
     *
     * If the body is a synchronous closure, this method evaluates the closure synchronously.
     * Otherwise, the body is asynchronous and the method schedules another thread to evaluate the body.
     *
     * @param currentThread
     *      The thread whose context the new thread will inherit.
     */
    @CpsVmThreadOnly
    /*package*/ void launch(CpsBodyInvoker params, CpsThread currentThread, FlowHead head) {

        StepStartNode sn = addBodyStartFlowNode(head);
        for (Action a : params.startNodeActions) {
            if (a!=null)
                sn.addAction(a);
        }
        head.setNewHead(sn);
        fireOnStart(sn);

        try {
            // TODO: handle arguments to closure
            Object x = params.body.getBody(currentThread).call();

            onSuccess.receive(x);   // body has completed synchronously
        } catch (CpsCallableInvocation e) {
            // execute this closure asynchronously
            // TODO: does it make sense that the new thread shares the same head?
            // this problem is captured as https://trello.com/c/v6Pbwqxj/70-allowing-steps-to-build-flownodes
            CpsThread t = currentThread.group.addThread(createContinuable(currentThread, e), head,
                    ContextVariableSet.from(currentThread.getContextVariables(), params.contextOverrides));

            startExecution(t);
        } catch (Throwable t) {
            // body has completed synchronously and abnormally
            onFailure.receive(t);
        }
    }

    /**
     * Inserts the flow node that indicates the beginning of the body invocation.
     *
     * @see CpsBodyExecution#addBodyEndFlowNode()
     */
    private StepStartNode addBodyStartFlowNode(FlowHead head) {
        StepStartNode start = new StepStartNode(head.getExecution(),
                context.getStepDescriptor(), head.get());
        start.addAction(new BodyInvocationAction());
        return start;
    }

    /**
     * Creates {@link Continuable} that executes the given invocation and pass its result to {@link FutureCallback}.
     *
     * The {@link Continuable} itself will just yield null. {@link CpsThreadGroup} considers the whole
     * execution a failure if any of the threads fail, so this behaviour ensures that a problem in the closure
     * body won't terminate the workflow.
     */
    private Continuable createContinuable(CpsThread currentThread, CpsCallableInvocation inv) {
        // we need FunctionCallEnv that acts as the back drop of try/catch block.
        // TODO: we need to capture the surrounding calling context to capture variables, and switch to ClosureCallEnv

        FunctionCallEnv caller = new FunctionCallEnv(null, onSuccess, null, null);
        if (currentThread.getExecution().isSandbox())
            caller.setInvoker(new SandboxInvoker());

        // catch an exception thrown from body and treat that as a failure
        TryBlockEnv env = new TryBlockEnv(caller, null);
        env.addHandler(Throwable.class, onFailure);

        return new Continuable(
            // this source location is a place holder for the step implementation.
            // perhaps at some point in the future we'll let the Step implementation control this.
            inv.invoke(env, SourceLocation.UNKNOWN, onSuccess));
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

    private void setOutcome(Outcome o) {
        synchronized (this) {
            if (outcome!=null)
                throw new IllegalStateException("Outcome is already set");
            this.outcome = o;
            notifyAll();    // wake up everyone waiting for the outcome.
        }
        context.saveState();
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

            setOutcome(new Outcome(null,t));
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

            setOutcome(new Outcome(o,null));
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
     * @see #addBodyStartFlowNode(FlowHead)
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
