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
import com.google.common.util.concurrent.ListenableFuture;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;

/**
 * An implicit context available to every {@link Step}.
 * In a flow, would be created by a {@code FlowExecution}.
 * It is serializable so a step which may survive a Jenkins restart is free to save it.
 *
 * <p>
 * {@link StepContext} is only valid until the result is set via {@link FutureCallback}.
 * Beyond that point, callers are not allowed to call any of the methods.
 */
public abstract class StepContext implements FutureCallback<Object>, Serializable {

    /**
     * Tries to find a contextually available object.
     * Suggested types:
     * <dl>
     * <dt>{@link Launcher}<dd>a way to fork processes
     * <dt>{@link EnvVars}<dd>read access to environment variables associated with a run, typically used for launchers
     * <dt>{@link FilePath}<dd>a “workspace” to use for example as from {@link hudson.Launcher.ProcStarter#pwd(hudson.FilePath)}
     * <dt>{@link Computer}<dd>a slave we are running on
     * <dt>{@link Executor}<dd>an executor slot on a slave we are running on
     * <dt>{@link TaskListener}<dd>a place to send output (see {@code LogAction} for a flow)
     * <dt>{@link Run}<dd>a running build
     * <dt>{@code FlowExecution} a running flow
     * <dt>{@code FlowNode}<dd>a running node in a flow
     * </dl>
     * @param key the kind of thing we want
     * @return that service, if available (which it should be if {@link StepDescriptor#getRequiredContext} includes it), else null
     *
     * @throws IOException
     *      If {@link StepContext} gets serialized, Jenkins restarts, deserialized later, and
     *      {@link Step} tries to get additional context objects, it can be that the corresponding
     *      {@code FlowExecution} could be non-existent (for example, the run that was driving the flow
     *      could have failed to load.) This exception is thrown in such situations just like
     *      {@code FlowExecutionOwner.get} throws IOException.
     */
    public abstract @Nullable <T> T get(Class<T> key) throws IOException, InterruptedException;

    @Override public abstract void onSuccess(@Nullable Object result);

    /**
     * Whether {@link #get} is ready to return values.
     * May be called to break deadlocks during reloading.
     * @return true normally, false if we are still reloading the context, for example during unpickling
     */
    public abstract boolean isReady() throws IOException, InterruptedException;

    /**
     * Requests that any state held by the {@link StepExecution} be saved to disk.
     * Useful when a long-running step has changed some instance fields (or the content of a final field) and needs these changes to be recorded.
     * An implementation might in fact save more state than just the associated step execution, but it must save at least that much.
     * @return a future letting you know if and when the state was in fact saved
     */
    public abstract ListenableFuture<Void> saveState();

    /**
     * Sets the overall result of the flow.
     * Like {@link Run#setResult}, can only make the result worse than it already is.
     * Since some flows may have try-catch semantics, if a step fails to complete normally it is better to use {@link #onFailure(Throwable)} instead.
     * (For example with {@link FlowInterruptedException}.)
     * @param r {@link Result#UNSTABLE}, typically
     */
    public abstract void setResult(Result r);

    /**
     * Prepares for an asynchronous invocation of the body block that's given as an argument
     * to this step invocation (in a host language dependent manner.)
     *
     * <p>
     * When the block is completed normally or abnormally, you can have
     * {@linkplain BodyInvoker#withCallback(FutureCallback) have the callback invoked}.
     * The invocation will not get scheduled until {@linkplain BodyInvoker#start() you start it}.
     *
     * <p>
     * {@link StepDescriptor#takesImplicitBlockArgument} must be true.
     */
    public abstract BodyInvoker newBodyInvoker();

    /**
     * {@link StepContext}s get persisted, so they may not have the identity equality, but equals
     * method would allow two instances to be compared.
     *
     * @return
     *      true if {@link StepContext}s are for the same context for the same execution.
     */
    public abstract boolean equals(Object o);

    /**
     * Needs to be overridden as the {@link #equals(Object)} method is overridden.
     */
    @Override
    public abstract int hashCode();

    private static final long serialVersionUID = 1L;
}
