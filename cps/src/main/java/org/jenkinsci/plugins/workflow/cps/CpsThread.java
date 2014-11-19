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

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Outcome;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.SettableFuture;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static java.util.logging.Level.*;
import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;

/**
 * Represents a {@link Continuable} that is either runnable or suspended (that waits for an
 * external event.)
 *
 * @author Kohsuke Kawaguchi
 */
@PersistIn(PROGRAM)
public final class CpsThread implements Serializable {
    /**
     * Owner object. A thread always belong to a {@link CpsThreadGroup}
     */
    @Nonnull
    final CpsThreadGroup group;

    /**
     * Unique ID of this thread among all the threads in the past or future under the same {@link CpsThreadGroup}.
     * This acts as a persistable handle for {@link CpsStepContext} to
     * {@linkplain CpsStepContext#getThread(CpsThreadGroup) refer back to the thread},
     * because they are persisted separately.
     */
    public final int id;

    /**
     * Represents the remaining computation.
     */
    private volatile Continuable program;

    /**
     * The value that feeds into the next execution of {@link #program}. Even though this is an input
     * from this class' point of view, it's typed as {@link Outcome} because from the CPS-transformed
     * program's point of view, this value acts as a return value (or an exception thrown)
     * from {@link Continuable#suspend(Object)}
     */
    Outcome resumeValue;

    /**
     * Promise that {@link Continuable#run0(Outcome)} gets eventually invoked with {@link #resumeValue}.
     */
    private transient SettableFuture<Object> promise;

    /**
     * The head of the flow node graph that this thread is growing.
     *
     * <p>
     * We create {@link CpsThread}s liberally in {@link CpsBodyExecution#launch(CpsBodyInvoker, CpsThread, FlowHead)},
     * and so multiple {@link CpsThread}s often share the same flow head.
     */
    final FlowHead head;

    @Nullable
    private final ContextVariableSet contextVariables;

    /**
     * If this thread is waiting for a {@link StepExecution} to complete (by invoking our callback),
     * this field is set to that execution.
     */
    private StepExecution step;

    /**
     * Gets called when the thread is done.
     */
    private final List<FutureCallback<Object>> completionHandlers = new ArrayList<FutureCallback<Object>>();

    CpsThread(CpsThreadGroup group, int id, Continuable program, FlowHead head, ContextVariableSet contextVariables) {
        this.group = group;
        this.id = id;
        this.program = group.getExecution().isSandbox() ? new SandboxContinuable(program,this) : program;
        this.head = head;
        this.contextVariables = contextVariables;
    }

    public CpsThreadGroup getGroup() {
        return group;
    }

    public CpsFlowExecution getExecution() {
        return group.getExecution();
    }

    <T> T getContextVariable(Class<T> type) {
        if (contextVariables==null)     return null;
        return contextVariables.get(type);
    }

    public ContextVariableSet getContextVariables() {
        return contextVariables;
    }

    boolean isRunnable() {
        return resumeValue!=null;
    }

    public StepExecution getStep() {
        return step;
    }

    /*package*/ void setStep(StepExecution step) {
        this.step = step;
    }

    /**
     * Executes CPS code synchronously a little bit more, until it hits
     * the point the workflow needs to be dehydrated.
     */
    @Nonnull Outcome runNextChunk() throws IOException {
        assert program!=null;

        while (true) {
            Outcome outcome;

            final CpsThread old = CURRENT.get();
            CURRENT.set(this);

            try {
                LOGGER.log(FINE, "runNextChunk on {0}", resumeValue);
                Outcome o = resumeValue;
                resumeValue = null;
                outcome = program.run0(o);
                if (outcome.getAbnormal() != null) {
                    LOGGER.log(FINE, "ran and produced error", outcome.getAbnormal());
                } else {
                    LOGGER.log(FINE, "ran and produced {0}", outcome);
                }
            } finally {
                CURRENT.set(old);
            }

            if (outcome.getNormal() instanceof ThreadTask) {
                // if an execution in the thread safepoint is requested, deliver that
                ThreadTask sc = (ThreadTask) outcome.getNormal();
                ThreadTaskResult r = sc.eval(this);
                if (r.resume!=null) {
                    // keep evaluating the CPS code
                    resumeValue = r.resume;
                    continue;
                } else {
                    // break but with a different value
                    outcome = r.suspend;
                }
            }


            if (promise!=null) {
                if (outcome.isSuccess())        promise.set(outcome.getNormal());
                else {
                    try {
                        promise.setException(outcome.getAbnormal());
                    } catch (Error e) {
                        if (e==outcome.getAbnormal()) {
                            // SettableFuture tries to rethrow an Error, which we don't want.
                            // so prevent that from happening. I need to see if this behaviour
                            // affects other places that use SettableFuture
                            ;
                        } else {
                            throw e;
                        }
                    }
                }
                promise = null;
            }

            return outcome;
        }
    }

    /**
     * Does this thread still have something to execute?
     * (as opposed to have finished running, either normally or abnormally?)
     */
    boolean isAlive() {
        return program.isResumable();
    }

    @CpsVmThreadOnly
    void addCompletionHandler(FutureCallback<Object> h) {
        if (!(h instanceof Serializable))
            throw new IllegalArgumentException(h.getClass()+" is not serializable");
        completionHandlers.add(h);
    }

    @CpsVmThreadOnly
    void fireCompletionHandlers(Outcome o) {
        for (FutureCallback<Object> h : completionHandlers) {
            if (o.isSuccess())  h.onSuccess(o.getNormal());
            else                h.onFailure(o.getAbnormal());
        }
    }

    /**
     * Finds the next younger {@link CpsThread} that shares the same {@link FlowHead}.
     *
     * Can be {@code this.}
     */
    @CheckForNull CpsThread getNextInner() {
        for (CpsThread t : group.threads.values()) {
            if (t.id <= this.id) continue;
            if (t.head==this.head)  return t;
        }
        return null;
    }

    /**
     * Schedules the execution of this thread from the last {@linkplain Continuable#suspend(Object)} point.
     *
     * @return
     *      Future that promises the completion of the next {@link #runNextChunk()}.
     */
    public Future<Object> resume(Outcome v) {
        assert resumeValue==null;
        resumeValue = v;
        promise = SettableFuture.create();
        group.scheduleRun();
        return promise;
    }

    private static final Logger LOGGER = Logger.getLogger(CpsThread.class.getName());

    private static final long serialVersionUID = 1L;

    private static final ThreadLocal<CpsThread> CURRENT = new ThreadLocal<CpsThread>();

    /**
     * While {@link CpsThreadGroup} executes, this method returns {@link CpsThread}
     * that's running.
     */
    @CpsVmThreadOnly
    public static CpsThread current() {
        return CURRENT.get();
    }

    @Override public String toString() {
        // getExecution().getOwner() would be useful but seems problematic.
        return "Thread #" + id + String.format(" @%h", this);
    }
}
