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
import hudson.EnvVars;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

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
     * <p>Note that for only one instance of a given class can be in context at a time.
     * Thus for certain types, an invoker will generally need to look up any instance in its own enclosing context amd create a proxy/merge:
     * <dl>
     * <dt>{@link EnvVars}<dd>use {@link EnvironmentExpander} instead
     * <dt>{@link EnvironmentExpander}<dd>use {@link EnvironmentExpander#merge}
     * <dt>{@link ConsoleLogFilter}<dd>use {@link #mergeConsoleLogFilters}
     * <dt>{@link LauncherDecorator}<dd>use {@link #mergeLauncherDecorators}
     * </dl>
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
     * Registers a callback that tracks the progress of the body execution.
     *
     * @return this object
     */
    public abstract BodyInvoker withCallback(BodyExecutionCallback callback);

    /**
     * @deprecated Use {@link #withCallback(BodyExecutionCallback)} and call {@link BodyExecutionCallback#wrap} if this is what you really wanted.
     */
    public final BodyInvoker withCallback(FutureCallback<Object> callback) {
        return withCallback(BodyExecutionCallback.wrap(callback));
    }

    /**
     * Schedules an asynchronous invocation of the body that's given as an argument
     * to the step invocation (in a host language dependent manner), with settings
     * configured on this object via other methods.
     */
    public abstract BodyExecution start();

    /**
     * Merge two console log filters so that both are applied.
     * @param original the original filter in {@link StepContext#get}, if any
     * @param subsequent your implementation; should expect {@code null} for the {@code build} parameter, and be {@link Serializable}
     * @return a merge of the two, or just yours if there was no original
     * @see #withContext
     */
    public static ConsoleLogFilter mergeConsoleLogFilters(@CheckForNull ConsoleLogFilter original, @Nonnull ConsoleLogFilter subsequent) {
        if (original == null) {
            return subsequent;
        }
        return new MergedFilter(original, subsequent);
    }
    private static final class MergedFilter extends ConsoleLogFilter implements Serializable {
        private static final long serialVersionUID = 1;
        private final ConsoleLogFilter original, subsequent;
        MergedFilter(ConsoleLogFilter original, ConsoleLogFilter subsequent) {
            this.original = original;
            this.subsequent = subsequent;
        }
        @SuppressWarnings("rawtypes") // not my fault
        @Override public OutputStream decorateLogger(AbstractBuild _ignore, OutputStream logger) throws IOException, InterruptedException {
            return subsequent.decorateLogger(_ignore, original.decorateLogger(_ignore, logger));
        }
    }

    /**
     * Merge two launcher decorators so that both are applied.
     * @param original the original decorator in {@link StepContext#get}, if any
     * @param subsequent your implementation; should be {@link Serializable}
     * @return a merge of the two, or just yours if there was no original
     * @see #withContext
     */
    public static LauncherDecorator mergeLauncherDecorators(@CheckForNull LauncherDecorator original, @Nonnull LauncherDecorator subsequent) {
        if (original == null) {
            return subsequent;
        }
        return new MergedLauncherDecorator(original, subsequent);
    }
    private static final class MergedLauncherDecorator extends LauncherDecorator implements Serializable {
        private static final long serialVersionUID = 1;
        private final LauncherDecorator original, subsequent;
        MergedLauncherDecorator(LauncherDecorator original, LauncherDecorator subsequent) {
            this.original = original;
            this.subsequent = subsequent;
        }
        @Override public Launcher decorate(Launcher launcher, Node node) {
            return subsequent.decorate(original.decorate(launcher, node), node);
        }
    }

}
