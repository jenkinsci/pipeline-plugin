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
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

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
     * <dt>{@link VirtualChannel}<dd>a way to run remote commands
     * <dt>{@link TaskListener}<dd>a place to send output (see {@code LogAction} for a flow)
     * <dt>{@code AtomNode}<dd>a running node in a flow
     * <dt>{@link Run}<dd>a running build
     * <dt>{@code FlowExecution} a running flow
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

    public abstract Object getGlobalVariable(String name);
    public abstract void setGlobalVariable(String name, Object v);
    // TODO enumerate variable names

    /**
     * Sets the overall result of the flow.
     * Like {@link Run#setResult}, can only make the result worse than it already is.
     * Since some flows may have try-catch semantics, if a step fails to complete normally it is better to use {@link #onFailure(Throwable)} instead.
     * @param r {@link Result#UNSTABLE}, typically
     */
    public abstract void setResult(Result r);

    /**
     * Schedules an asynchronous invocation of the body block that's given as an argument
     * to this step invocation (in a host language dependent manner), then when the block
     * is completed normally or abnormally, invoke the given callback.
     *
     * <p>
     * {@link StepDescriptor#takesImplicitBlockArgument} must be true.
     *
     * @param contextOverrides
     *      Overrides to the context values that are in effect while evaluating the body.
     *      Only allowed values are instances of the predefined types (see {@link #get(Class)} above.)
     *      TODO: more restrictive list here; do NOT pass Launcher
     */
    public abstract void invokeBodyLater(FutureCallback<Object> callback, Object... contextOverrides);

    private static final long serialVersionUID = 1L;
}
