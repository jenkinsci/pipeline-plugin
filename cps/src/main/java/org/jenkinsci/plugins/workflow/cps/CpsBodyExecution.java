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
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import javax.annotation.CheckForNull;
import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import static java.util.logging.Level.*;
import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;

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
    @GuardedBy("this") // 'thread' and 'stopped' needs to be compared & set atomically
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

    private final Continuation onSuccess = new SuccessAdapter();

    /**
     * Unlike {@link #onSuccess} that can only happen after {@link #launch(CpsBodyInvoker, CpsThread, FlowHead)},
     * a failure can happen right after {@link CpsBodyInvoker#start()} before we get a chance to be launched.
     */
    /*package*/ final Continuation onFailure = new FailureAdapter();

    @GuardedBy("this")
    private Outcome outcome;

    /**
     * @see CpsBodyInvoker#createBodyBlockNode
     */
    private final boolean createBodyBlockNode;

    public CpsBodyExecution(CpsStepContext context, List<BodyExecutionCallback> callbacks, boolean createBodyBlockNode) {
        this.context = context;
        this.callbacks = callbacks;
        this.createBodyBlockNode = createBodyBlockNode;
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
        if (isLaunched())
            throw new IllegalStateException("Already launched");

        StepStartNode sn = addBodyStartFlowNode(head);
        for (Action a : params.startNodeActions) {
            if (a!=null)
                sn.addAction(a);
        }

        StepContext sc = subContext(sn);
        for (BodyExecutionCallback c : callbacks) {
            c.onStart(sc);
        }

        try {
            // TODO: handle arguments to closure
            Object x = params.body.getBody(currentThread).call();

            // body has completed synchronously. mark this done after the fact
            // pointless synchronization to make findbugs happy. This is already done, so there's no cancelling this anyway.
            synchronized (this) {
                this.thread = currentThread;
            }
            onSuccess.receive(x);
        } catch (CpsCallableInvocation e) {
            // execute this closure asynchronously
            // TODO: does it make sense that the new thread shares the same head?
            CpsThread t = currentThread.group.addThread(createContinuable(currentThread, e), head,
                    ContextVariableSet.from(currentThread.getContextVariables(), params.contextOverrides));

            // let the new CpsThread run. Either get the new thread going normally with (null,null), or abort from the beginning
            // due to earlier cancellation
            synchronized (this) {
                t.resume(new Outcome(null, stopped));
                assert this.thread==null;
                this.thread = t;
            }
        } catch (Throwable t) {
            // body has completed synchronously and abnormally
            onFailure.receive(t);
        }
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
                        LOGGER.log(WARNING, "Failed to stop " + s, e);
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

    /**
     * Is the execution under way? True after {@link #launch(CpsBodyInvoker, CpsThread, FlowHead)}
     */
    public synchronized boolean isLaunched() {
        return thread!=null;
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

    public synchronized boolean isDone() {
        return outcome!=null;
    }

    private class FailureAdapter implements Continuation {
        @Override
        public Next receive(Object o) {
            if (!isLaunched()) {
                // failed before we even started. fake the start node that start() would have created.
                addBodyStartFlowNode(CpsThread.current().head);
            }

            StepEndNode en = addBodyEndFlowNode();

            Throwable t = (Throwable)o;
            en.addAction(new ErrorAction(t));

            setOutcome(new Outcome(null,t));
            StepContext sc = subContext(en);
            for (BodyExecutionCallback c : callbacks) {
                c.onFailure(sc, t);
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
            StepContext sc = subContext(en);
            for (BodyExecutionCallback c : callbacks) {
                c.onSuccess(sc, o);
            }

            return Next.terminate(null);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Creates a sub-context to call {@link BodyExecutionCallback}.
     * If {@link #createBodyBlockNode} is false, then we don't have distinctive
     * {@link FlowNode}, so we just hand out the master context.
     */
    private StepContext subContext(FlowNode n) {
        if (n==null)
            return context;
        else
            return new CpsBodySubContext(context,n);
    }

    /**
     * Inserts the flow node that indicates the beginning of the body invocation.
     *
     * @see #addBodyEndFlowNode()
     */
    private @CheckForNull StepStartNode addBodyStartFlowNode(FlowHead head) {
        if (createBodyBlockNode) {
            StepStartNode start = new StepStartNode(head.getExecution(),
                    context.getStepDescriptor(), head.get());
            this.startNodeId = start.getId();
            start.addAction(new BodyInvocationAction());
            head.setNewHead(start);
            return start;
        } else {
            return null;
        }
    }

    /**
     * Inserts the flow node that indicates the beginning of the body invocation.
     *
     * @see #addBodyStartFlowNode(FlowHead)
     */
    private @CheckForNull StepEndNode addBodyEndFlowNode() {
        if (createBodyBlockNode) {
            try {
                FlowHead head = CpsThread.current().head;

                StepEndNode end = new StepEndNode(head.getExecution(),
                        getBodyStartNode(), head.get());
                end.addAction(new BodyInvocationAction());
                head.setNewHead(end);

                return end;
            } catch (IOException e) {
                LOGGER.log(WARNING, "Failed to grow the flow graph", e);
                throw new Error(e);
            }
        } else {
            return null;
        }
    }

    public StepStartNode getBodyStartNode() throws IOException {
        if (startNodeId==null)
            throw new IllegalStateException("StepStartNode is not yet created");
        CpsThread t;
        synchronized (this) {// to make findbugs happy
            t = thread;
        }
        return (StepStartNode) t.getExecution().getNode(startNodeId);
    }


    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(CpsBodyExecution.class.getName());
}
