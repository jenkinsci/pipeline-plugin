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

import java.io.Serializable;
import java.util.Collection;

/**
 * Represents the executing body block of {@link Step}.
 *
 * @author Kohsuke Kawaguchi
 * @see StepContext#invokeBodyLater(Object...)
 */
public abstract class BodyExecution implements Serializable {
    /**
     * Returns the inner-most {@link StepExecution}s that are currently executing.
     */
    public abstract Collection<StepExecution> getCurrentExecutions();

    /**
     * Adds a callback that gets invoked when the body finishes execution.
     *
     * If the execution is already completed, the callback will be invoked immediately.
     */
    public abstract void addCallback(FutureCallback<Object> callback);

    /**
     * Has the body finished executing?
     */
    public abstract boolean isDone();

    /**
     * Convenience method to interrupt this execution.
     */
    public abstract void stop() throws Exception;

    private static final long serialVersionUID = 1L;
}
