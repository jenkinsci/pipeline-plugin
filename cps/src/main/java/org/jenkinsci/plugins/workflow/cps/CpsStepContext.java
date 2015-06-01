/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Outcome;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import groovy.lang.Closure;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.support.DefaultStepContext;
import org.jenkinsci.plugins.workflow.support.concurrent.Futures;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.ContextResettingExecutorService;
import org.codehaus.groovy.runtime.InvokerInvocationException;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;

/**
 * {@link StepContext} implementation for CPS.
 *
 * <p>
 * This context behaves in two modes. It starts in the synchronous mode, where if a result is set (or exception
 * is thrown), it just gets recoded. When passed into {@link Step#start(StepContext)}, it's in this mode.
 *
 * <p>
 * When {@link Step#start(StepContext)} method returns, we'll atomically check if the result is set or not
 * and then switch to the asynchronous mode. In this mode, if the result is set, it'll trigger the rehydration
 * of the workflow. If a {@link CpsStepContext} gets serialized, it'll be deserialized in the asynchronous mode.
 *
 * <p>
 * This object must be serializable on its own without sucking in any of the {@link CpsFlowExecution} object
 * graph. Wherever we need {@link CpsFlowExecution} we do that by following {@link FlowExecutionOwner}, and
 * when we need pointers to individual objects inside, we use IDs (such as {@link #id}}.
 *
 * @author Kohsuke Kawaguchi
 * @see Step#start(StepContext)
 */
@PersistIn(ANYWHERE)
@edu.umd.cs.findbugs.annotations.SuppressWarnings("SE_TRANSIENT_FIELD_NOT_RESTORED") // bodyInvokers, syncMode handled specially
public class CpsStepContext extends DefaultStepContext { // TODO add XStream class mapper

    private static final Logger LOGGER = Logger.getLogger(CpsStepContext.class.getName());

    @GuardedBy("this")
    private transient Outcome outcome;

    // see class javadoc.
    // transient because if it's serialized and deserialized, it should come back in the async mode.
    private transient boolean syncMode = true;

    /**
     * This object gets serialized independently from the rest of {@link CpsFlowExecution}
     * and {@link DSL}, so it needs to use a handle to refer back to {@link CpsFlowExecution}
     */
    private final FlowExecutionOwner executionRef;

    /**
     * {@link FlowNode#id} that points to the atom node created for this step.
     */
    private final String id;

    /**
     * Keeps an in-memory reference to {@link FlowNode} to speed up the synchronous execution.
     *
     * If there's a body, this field is {@link BlockStartNode}. If there's no body, then this
     * field is {@link AtomNode}
     *
     * @see #getNode()
     */
    /*package*/ transient FlowNode node;

    /*

        TODO: parallel step implementation

        when forking off another branch of parallel, call the 3-arg version of the start() method,
        and have its callback insert the ID of the new head at the end of the thread
     */
    /**
     * {@link FlowHead#getId()} that should become
     * the parents of the {@link BlockEndNode} when we create one. Only used when this context has the body.
     */
    final List<Integer> bodyHeads = new ArrayList<Integer>();

    /**
     * If the invocation of the body is requested, this object remembers how to start it.
     *
     * <p>
     * Only used in the synchronous mode while {@link CpsFlowExecution} is in the RUNNABLE state,
     * so this need not be persisted. To preserve the order of invocation in the flow graph,
     * this needs to be a list and not set.
     */
    transient List<CpsBodyInvoker> bodyInvokers = Collections.synchronizedList(new ArrayList<CpsBodyInvoker>());

    /**
     * While {@link CpsStepContext} has not received teh response, maintains the body closure.
     *
     * This is the implicit closure block passed to the step invocation.
     */
    private BodyReference body;

    private final int threadId;

    /**
     * {@linkplain Descriptor#getId() step descriptor ID}.
     */
    private final String stepDescriptorId;

    /**
     * Resolved result of {@link #stepDescriptorId} to make the look up faster.
     */
    private transient volatile StepDescriptor stepDescriptor;

    /**
     * Cached value of {@link #getProgramPromise}.
     * Never null once set (might be overwritten).
     */
    private transient volatile ListenableFuture<CpsThreadGroup> programPromise;

    @CpsVmThreadOnly
    CpsStepContext(StepDescriptor step, CpsThread thread, FlowExecutionOwner executionRef, FlowNode node, Closure body) {
        this.threadId = thread.id;
        this.executionRef = executionRef;
        this.id = node.getId();
        this.node = node;
        this.body = thread.group.export(body);
        this.stepDescriptorId = step.getId();
    }

    /**
     * Obtains {@link StepDescriptor} that represents the step this context is invoking.
     *
     * @return
     *      This method returns null if the step descriptor used is not recoverable in the current VM session,
     *      such as when the plugin that implements this was removed. So the caller should defend against null.
     */
    public @CheckForNull StepDescriptor getStepDescriptor() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            return null;
        }
        if (stepDescriptor==null)
            stepDescriptor = (StepDescriptor) j.getDescriptor(stepDescriptorId);
        return stepDescriptor;
    }

    public String getDisplayName() {
        StepDescriptor d = getStepDescriptor();
        return d!=null ? d.getDisplayName() : stepDescriptorId;
    }

    @Override protected CpsFlowExecution getExecution() throws IOException {
        return (CpsFlowExecution)executionRef.get();
    }

    /**
     * Returns the thread that is executing this step.
     * Needs to take {@link CpsThreadGroup} as a parameter to prove that the caller is in CpsVmThread.
     *
     * @return
     *      null if the thread has finished executing.
     */
    @CheckForNull CpsThread getThread(CpsThreadGroup g) {
        CpsThread thread = g.threads.get(threadId);
        if (thread == null) {
            LOGGER.log(Level.FINE, "no thread " + threadId + " among " + g.threads.keySet(), new IllegalStateException());
        }
        return thread;
    }

    /**
     * Synchronously resolve the current thread.
     *
     * This can block for the entire duration of the PREPARING state.
     */
    private @CheckForNull CpsThread getThreadSynchronously() throws InterruptedException, IOException {
        try {
            CpsThreadGroup g = getProgramPromise().get();
            return getThread(g);
        } catch (ExecutionException e) {
            throw new IOException(e);
        }
    }

    private static final ExecutorService getProgramPromiseExecutorService = new ContextResettingExecutorService(Executors.newCachedThreadPool(new NamingThreadFactory(new DaemonThreadFactory(), "CpsStepContext.getProgramPromise")));
    private @Nonnull ListenableFuture<CpsThreadGroup> getProgramPromise() {
        if (programPromise == null) {
            final SettableFuture<CpsThreadGroup> f = SettableFuture.create();
            // TODO is there some more convenient way of writing this using Futures.transform? FlowExecutionOwner.get should really return ListenableFuture<FlowExecution>
            getProgramPromiseExecutorService.submit(new Runnable() {
                @Override public void run() {
                    try {
                        ListenableFuture<CpsThreadGroup> pp;
                        CpsFlowExecution flowExecution = getFlowExecution();
                        while ((pp = flowExecution.programPromise) == null) {
                            Thread.sleep(100); // TODO why is this occasionally not set right away?
                        }
                        f.set(pp.get());
                    } catch (Throwable x) { // from getFlowExecution() or get()
                        f.setException(x);
                    }
                }
            });
            programPromise = f;
        }
        return programPromise;
    }

    @Override public boolean isReady() {
        return getProgramPromise().isDone();
    }

    @Override
    public CpsBodyInvoker newBodyInvoker() {
        return newBodyInvoker(body);
    }

    public CpsBodyInvoker newBodyInvoker(BodyReference body) {
        if (body==null)
            throw new IllegalStateException("There's no body to invoke");
        return new CpsBodyInvoker(this,body);
    }

    @Override
    protected <T> T doGet(Class<T> key) throws IOException, InterruptedException {
        CpsThread t = getThreadSynchronously();
        if (t == null) {
            throw new IOException("cannot find current thread");
        }

        T v = t.getContextVariable(key);
        if (v!=null)        return v;

        if (FlowNode.class.isAssignableFrom(key)) {
            return key.cast(getNode());
        }
        if (key == CpsThread.class) {
            return key.cast(t);
        }
        if (key == CpsThreadGroup.class) {
            return key.cast(t.group);
        }
        return null;
    }

    @Override protected FlowNode getNode() throws IOException {
        if (node == null) {
            node = getFlowExecution().getNode(id);
            if (node == null) {
                throw new IOException("no node found for " + id);
            }
        }
        return node;
    }

    @Override public synchronized void onFailure(Throwable t) {
        if (t == null) {
            throw new IllegalArgumentException();
        }
        if (isCompleted()) {
            LOGGER.log(Level.WARNING, "already completed " + this, new IllegalStateException(t));
            return;
        }
        this.outcome = new Outcome(null,t);

        scheduleNextRun();
    }

    @Override public synchronized void onSuccess(Object returnValue) {
        if (isCompleted()) {
            LOGGER.log(Level.WARNING, "already completed " + this, new IllegalStateException());
            return;
        }
        this.outcome = new Outcome(returnValue,null);

        scheduleNextRun();
    }

    /**
     * When this step context has completed execution (successful or otherwise), plan the next action.
     */
    private void scheduleNextRun() {
        if (syncMode) {
            // if we get the result set before the start method returned, then DSL.invokeMethod() will
            // plan the next action.
            return;
        }

        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null && jenkins.isQuietingDown()) {
            // TODO would like to just return now, but that makes the flow hang after restart
        }

        try {
            final FlowNode n = getNode();
            final CpsFlowExecution flow = getFlowExecution();

            final List<FlowNode> parents = new ArrayList<FlowNode>();
            for (int head : bodyHeads) {
                parents.add(flow.getFlowHead(head).get());
            }

            flow.runInCpsVmThread(new FutureCallback<CpsThreadGroup>() {
                @CpsVmThreadOnly
                @Override
                public void onSuccess(CpsThreadGroup g) {
                    g.unexport(body);
                    body = null;
                    CpsThread thread = getThread(g);
                    if (thread != null) {
                        CpsThread nit = thread.getNextInner();
                        if (nit!=null) {
                            // can't mark this done until the inner thread is done.
                            // defer the processing until the inner thread is done
                            nit.addCompletionHandler(new ScheduleNextRun());
                            if (getOutcome().isFailure()) {
                                // if the step with a currently running body reported a failure,
                                // make some effort to try to interrupt the running body
                                StepExecution s = nit.getStep();
                                if (s != null) {
                                    // TODO: ideally this needs to work like interrupt, in that
                                    // if s==null the next StepExecution gets interrupted when it happen
                                    FlowInterruptedException cause = new FlowInterruptedException(Result.FAILURE);
                                    cause.initCause(getOutcome().getAbnormal());
                                    try {
                                        s.stop(cause);
                                    } catch (Exception e) {
                                        LOGGER.log(Level.WARNING, "Failed to stop the body execution in response to the failure of the parent");
                                    }
                                }
                            }
                            return;
                        }

                        if (n instanceof StepStartNode) {
                            // if there's no body to invoke, we want the current thread to be the sole head
                            if (parents.isEmpty())
                                parents.add(thread.head.get());

                            // clear all the subsumed heads that are joining. thread that owns parents.get(0) lives on
                            for (int i=1; i<parents.size(); i++)
                                g.getExecution().subsumeHead(parents.get(i));
                            thread.head.setNewHead(new StepEndNode(flow, (StepStartNode) n, parents));
                        }
                        thread.head.markIfFail(getOutcome());
                        thread.setStep(null);
                        thread.resume(getOutcome());
                    }
                }

                /**
                 * Program state failed to load.
                 */
                @Override
                public void onFailure(Throwable t) {
                }
            });
        } catch (IOException x) {
            LOGGER.log(Level.FINE, null, x);
        }
    }

    @Override
    public void setResult(Result r) {
        try {
            getFlowExecution().setResult(r);
        } catch (IOException x) {
            LOGGER.log(Level.FINE, null, x);
        }
    }

    private @Nonnull CpsFlowExecution getFlowExecution() throws IOException {
        return (CpsFlowExecution)executionRef.get();
    }

    synchronized boolean isCompleted() {
        return outcome!=null;
    }

    synchronized boolean isSyncMode() {
        return syncMode;
    }

    /**
     * Simulates the result of the {@link StepContext call} by either throwing an exception
     * or returning the value.
     */
    synchronized Object replay() {
        try {
            return getOutcome().replay();
        } catch (Throwable failure) {
            // Cf. CpsBodyExecution.FailureAdapter:
            if (failure instanceof RuntimeException)
                throw (RuntimeException) failure;
            if (failure instanceof Error)
                throw (Error) failure;
            // Any GroovyRuntimeException is treated magically by ScriptBytecodeAdapter.unwrap (from PogoMetaClassSite):
            throw new InvokerInvocationException(failure);
        }
    }

    synchronized Outcome getOutcome() {
        return outcome;
    }

    /**
     * Atomically switch this context into the asynchronous mode.
     * Any results set beyond this point will trigger callback.
     *
     * @return
     *      true if the result was not available prior to this call and the context was successfully switched to the
     *      async mode.
     *
     *      false if the result is already available. The caller should use {@link #getOutcome()} to obtain that.
     */
    synchronized boolean switchToAsyncMode() {
        if (!syncMode)  throw new AssertionError();
        syncMode = false;
        return !isCompleted();
    }

    @Override public ListenableFuture<Void> saveState() {
        try {
            final SettableFuture<Void> f = SettableFuture.create();
            getFlowExecution().runInCpsVmThread(new FutureCallback<CpsThreadGroup>() {
                @Override public void onSuccess(CpsThreadGroup result) {
                    try {
                        // TODO keep track of whether the program was saved anyway after saveState was called but before now, and do not bother resaving it in that case
                        result.saveProgram();
                        f.set(null);
                    } catch (IOException x) {
                        f.setException(x);
                    }
                }
                @Override public void onFailure(Throwable t) {
                    f.setException(t);
                }
            });
            return f;
        } catch (IOException x) {
            return Futures.immediateFailedFuture(x);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CpsStepContext that = (CpsStepContext) o;

        return executionRef.equals(that.executionRef) && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        int result = executionRef.hashCode();
        result = 31 * result + id.hashCode();
        return result;
    }

    @Override public String toString() {
        return "CpsStepContext[" + id + "]:" + executionRef;
    }

    private static final long serialVersionUID = 1L;

    @edu.umd.cs.findbugs.annotations.SuppressWarnings("SE_INNER_CLASS")
    private class ScheduleNextRun implements FutureCallback<Object>, Serializable {
        public void onSuccess(Object _)    { scheduleNextRun(); }
        public void onFailure(Throwable _) { scheduleNextRun(); }

        private static final long serialVersionUID = 1L;
    }
}
