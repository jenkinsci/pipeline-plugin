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
package org.jenkinsci.plugins.workflow.steps;

import com.google.common.util.concurrent.FutureCallback;
import jenkins.model.CauseOfInterruption;

import java.io.Serializable;
import java.util.Collection;
import java.util.concurrent.Future;

/**
 * Represents the executing body block of {@link Step}.
 *
 * <p>
 * As a representation of asynchronous computation, this object implements {@link Future},
 * so that you can cancel the execution, install a listener, etc.
 *
 * @author Kohsuke Kawaguchi
 * @see BodyInvoker#start()
 */
public abstract class BodyExecution implements Future<Object>, Serializable {
    // I wanted to make this extend from ListenableFuture, but its addListener method takes
    // Executor & Runnable that makes it unsuitable for persistence.

    /**
     * Returns the inner-most {@link StepExecution}s that are currently executing.
     */
    public abstract Collection<StepExecution> getCurrentExecutions();

    /**
     * Attempts to cancel an executing body block.
     *
     * <p>
     * If the body has finished executing, or is cancelled already, the attempt will
     * fail. This method is asynchronous. There's no guarantee that the cancellation
     * has happened or completed before this method returns.
     *
     * @return false if the task cannot be cancelled.
     */
    public abstract boolean cancel(CauseOfInterruption... causes);

    /**
     * @deprecated
     *      Use other overloaded forms of the cancel method to provide richer context.
     */
    public boolean cancel(boolean b) {
        return cancel(new Exception());
    }

    /**
     * Convenience method around {@link #cancel(CauseOfInterruption...)} in case
     * the cause is a random exception.
     */
    public boolean cancel(Throwable t) {
        return cancel(new ExceptionCause(t));
    }

    /**
     * Adds a callback that gets invoked when the body finishes execution.
     *
     * If the execution is already completed, the callback will be invoked immediately.
     */
    public abstract void addCallback(FutureCallback<Object> callback);

    private static final long serialVersionUID = 1L;
}
