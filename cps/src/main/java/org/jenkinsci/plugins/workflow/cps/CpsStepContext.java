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
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.support.actions.LogActionImpl;
import org.jenkinsci.plugins.workflow.support.concurrent.Futures;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import groovy.lang.Closure;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.util.StreamTaskListener;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;

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
 */
public class CpsStepContext extends StepContext { // TODO add XStream class mapper

    private static final Logger LOGGER = Logger.getLogger(CpsStepContext.class.getName());

    // guarded by 'this'
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
     * Keeps an in-memory reference to {@link AtomNode} to speed up the synchronous execution.
     *
     * @see #getNode()
     */
    /*package*/ transient AtomNode node;

    /**
     * If the invocation of the body is requested, this object remembers how to start it.
     *
     * <p>
     * Only used in the synchronous mode while {@link CpsFlowExecution} is in the RUNNABLE state,
     * so this need not be persisted.
     */
    transient BodyInvoker bodyInvoker;

    /**
     * While {@link CpsStepContext} has not received teh response, maintains the body closure.
     */
    private BodyReference body;

    /**
     * To prevent double instantiation of task listener, once we create it we keep it here.
     */
    private transient TaskListener listener;

    private final int threadId;

    CpsStepContext(CpsThread thread, FlowExecutionOwner executionRef, AtomNode node, Closure body) {
        this.threadId = thread.id;
        this.executionRef = executionRef;
        this.id = node.getId();
        this.node = node;
        this.body = thread.group.export(body);
    }

    private CpsFlowExecution getExecution() throws IOException {
        return (CpsFlowExecution)executionRef.get();
    }

    @CheckForNull CpsThread getThread(CpsThreadGroup g) {
        CpsThread thread = g.threads.get(threadId);
        if (thread == null) {
            LOGGER.log(Level.WARNING, "no thread {0} among {1}", new Object[] {threadId, g.threads.keySet()});
        }
        return thread;
    }

    /**
     * Synchronously resolve the current thread.
     *
     * This can block for the entire duration of the PREPARING state.
     */
    @CheckForNull CpsThread getThreadSynchronously() throws InterruptedException, IOException {
        try {
            CpsThreadGroup g = getFlowExecution().programPromise.get();
            return getThread(g);
        } catch (ExecutionException e) {
            throw new IOException(e);
        }
    }

    @Override public boolean isReady() throws IOException, InterruptedException {
        ListenableFuture<CpsThreadGroup> p = getFlowExecution().programPromise;
        return p.isDone();
    }

    @Override
    public void invokeBodyLater(final FutureCallback callback, Object... contextOverrides) {
        if (body==null)
            throw new IllegalStateException("There's no body to invoke");

        final BodyInvoker b = new BodyInvoker(body,callback,contextOverrides);

        if (syncMode) {
            // we process this in CpsThread#runNextChunk
            if (bodyInvoker!=null)
                throw new IllegalStateException("Trying to call invokeBodyLater twice");
            bodyInvoker = b;
        } else {
            try {
                Futures.addCallback(getExecution().programPromise, new FutureCallback<CpsThreadGroup>() {
                    @Override
                    public void onSuccess(CpsThreadGroup g) {
                        CpsThread thread = getThread(g);
                        if (thread != null) {
                            b.start(thread);
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        callback.onFailure(t);
                    }
                });
            } catch (IOException e) {
                callback.onFailure(e);
            }
        }
    }

    @Override
    public <T> T get(Class<T> key) throws IOException, InterruptedException {
        CpsThread t = getThreadSynchronously();
        if (t == null) {
            throw new IOException("cannot find current thread");
        }

        T v = t.getContextVariable(key);
        if (v!=null)        return v;

        if (key==TaskListener.class) {
            if (listener==null) {
                LogActionImpl la = getNode().getAction(LogActionImpl.class);
                if (la==null) {
                    // TODO: use the default charset of the contextual Computer object
                    la = new LogActionImpl(getNode(), Charset.defaultCharset());
                    getNode().addAction(la);
                }

                listener = new StreamTaskListener(new FileOutputStream(la.getLogFile(),true));
            }
            return key.cast(listener);
        }
        if (key==AtomNode.class || key==FlowNode.class) {
            return key.cast(getNode());
        }
        {// fallback logic to infer context variable from other sources
            // TODO: this logic should be consistent across StepContext impls, so it should be promoted to somewhere
            if (key==Node.class) {
                Computer c = get(Computer.class);
                if (c==null)    return null;
                return key.cast(c.getNode());
            }
            if (key==Run.class)
                return key.cast(getExecution().getOwner().getExecutable());
            if (key==Job.class)
                return key.cast(get(Run.class).getParent());
            if (key==FilePath.class) {
                Node n = get(Node.class);
                if (n==null)    return null;
                return key.cast(n.getWorkspaceFor((TopLevelItem) get(Job.class)));
            }
            if (key==Launcher.class) {
                Node n = get(Node.class);
                if (n==null)    return null;
                return key.cast(n.createLauncher(get(TaskListener.class)));
            }
            if (key==EnvVars.class)
                return key.cast(get(Run.class).getEnvironment(get(TaskListener.class)));
            if (key==FlowExecution.class)
                return key.cast(getExecution());
            if (key==CpsThread.class)
                return key.cast(t);
            if (key==CpsThreadGroup.class)
                return key.cast(t.group);
        }
        // unrecognized key
        return null;
    }

    private AtomNode getNode() throws IOException {
        if (node==null)
            node = (AtomNode)getFlowExecution().getNode(id);
        return node;
    }

    @Override
    public Object getGlobalVariable(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setGlobalVariable(String name, Object v) {
        throw new UnsupportedOperationException();
    }

    public synchronized void onFailure(Throwable t) {
        if (t==null)
            throw new IllegalArgumentException();
        if (isCompleted())
            throw new IllegalStateException("Already completed", t);
        this.outcome = new Outcome(null,t);

        scheduleNextRun();
    }

    public synchronized void onSuccess(Object returnValue) {
        if (isCompleted())
            throw new IllegalStateException("Already completed");
        this.outcome = new Outcome(returnValue,null);

        scheduleNextRun();
    }

    private void scheduleNextRun() {
        if (!syncMode) {
            try {
                if (node!=null) // TODO: if node is null I think we need to bring it back from the disk
                    node.markAsCompleted();
                Futures.addCallback(getFlowExecution().programPromise, new FutureCallback<CpsThreadGroup>() {
                    @Override
                    public void onSuccess(CpsThreadGroup g) {
                        g.unexport(body);
                        body = null;
                        CpsThread thread = getThread(g);
                        if (thread != null) {
                            thread.resume(getOutcome());
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                    }
                });
            } catch (IOException x) {
                LOGGER.log(Level.FINE, null, x);
            }
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

    private CpsFlowExecution getFlowExecution() throws IOException {
        return (CpsFlowExecution)executionRef.get();
    }

    synchronized boolean isCompleted() {
        return outcome!=null;
    }

    /**
     * Simulates the result of the {@link StepContext call} by either throwing an exception
     * or returning the value.
     */
    synchronized Object replay() {
        try {
            return getOutcome().replay();
        } catch (Throwable failure) {
            if (failure instanceof RuntimeException)
                throw (RuntimeException) failure;
            if (failure instanceof Error)
                throw (Error) failure;
            throw new RuntimeException(failure);
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

    private static final long serialVersionUID = 1L;
}
