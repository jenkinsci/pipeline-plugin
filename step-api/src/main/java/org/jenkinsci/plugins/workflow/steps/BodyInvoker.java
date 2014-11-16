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

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

/**
 * Builder pattern for controlling how to execute a body block of a {@link Step}.
 *
 * @author Kohsuke Kawaguchi
 * @see StepContext#newBodyInvoker()
 */
public abstract class BodyInvoker {
    /**
     * Overrides to the context values that are in effect while evaluating the body.
     * Only allowed values are instances of the predefined types.
     *
     * @see StepContext#get(Class)
     *
     * @return this object
     */
    public abstract BodyInvoker withContext(Object override);

    public BodyInvoker withContexts(Object... overrides) {
        return withContexts(Arrays.asList(overrides));
    }

    public BodyInvoker withContexts(Collection<?> overrides) {
        for (Object o : overrides)
            withContext(o);
        return this;
    }

    /**
     * Sets a human readable name that describes this body invocation.
     *
     * Specify {@code null} to designate that this body invocation is anonymous.
     *
     * <p>
     * When used from the workflow plugin, anonymous body invocation suppress the generation
     * of {@code BlockStartNode}/{@code BlockEndNode} to designate the body. This is
     * useful when a {@link Step} executes body at most once.
     *
     * @return this object
     */
    public abstract BodyInvoker withDisplayName(@Nullable String name);

    /**
     * The same calling {@link BodyExecution#addCallback(FutureCallback)} after {@link #start()}
     * but also defined here for better discoverability.
     *
     * @return this object
     */
    public abstract BodyInvoker withCallback(FutureCallback<Object> callback);

    /**
     * Schedules an asynchronous invocation of the body that's given as an argument
     * to the step invocation (in a host language dependent manner), with settings
     * configured on this object via other methods.
     */
    public abstract BodyExecution start();
}
