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

package org.jenkinsci.plugins.workflow.flow;

import com.google.common.util.concurrent.ListenableFuture;
import hudson.model.Executor;
import jenkins.model.CauseOfInterruption;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.graph.FlowActionStorage;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import hudson.model.Result;
import hudson.security.ACL;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import java.io.IOException;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;

/**
 * State of a currently executing workflow.
 *
 * <p>
 * This "interface" abstracts away workflow definition language, syntax, or its
 * execution model, but it allows other code to listen on what's going on.
 *
 * <h2>Persistence</h2>
 * <p>
 * {@link FlowExecution} must support persistence by XStream, which should
 * capture the state of execution at one point. The expectation is that when
 * the object gets deserialized, it'll start re-executing from that point.
 *
 *
 * @author Kohsuke Kawaguchi
 * @author Jesse Glick
 */
public abstract class FlowExecution implements FlowActionStorage {

    /**
     * Called after {@link FlowDefinition#create(FlowExecutionOwner, List)} to
     * initiate the execution. This method is not invoked on rehydrated execution.
     *
     * This separation ensures that {@link FlowExecutionOwner} is functional when this method
     * is called.
     */
    public abstract void start() throws IOException;

    /**
     * Should be called by the flow owner after it is deserialized.
     */
    public abstract void onLoad();

    public abstract FlowExecutionOwner getOwner();

    /**
     * In the current flow graph, return all the "head" nodes where the graph is still growing.
     *
     * If you think of a flow graph as a git repository, these heads correspond to branches.
     */
    // TODO: values are snapshot in time
    public abstract List<FlowNode> getCurrentHeads();

    /**
     * Yields the inner-most {@link StepExecution}s that are currently executing.
     *
     * {@link StepExecution}s are persisted as a part of the program state, so its lifecycle
     * is independent of {@link FlowExecution}, hence the asynchrony.
     *
     * Think of this as program counters of all the virtual threads.
     */
    public abstract ListenableFuture<List<StepExecution>> getCurrentExecutions();

    // TODO: there should be navigation between FlowNode <-> StepExecution

    /**
     * Short for {@code getCurrentHeads().contains(n)} but more efficient.
     */
    public abstract boolean isCurrentHead(FlowNode n);

    /**
     * Returns the URL of this {@link FlowExecution}, relative to the context root of Jenkins.
     *
     * @return
     *      String like "job/foo/32/execution/" with trailing slash but no leading slash.
     */
    public String getUrl() throws IOException {
        return getOwner().getUrlOfExecution();
    }

    /**
     * Interrupts the execution of a flow.
     *
     * If any computation is going on synchronously, it will be interrupted/killed/etc.
     * If it's in a suspended state waiting to be resurrected (such as waiting for
     * {@link StepContext#onSuccess(Object)}), then it just marks the workflow as done
     * with the specified status.
     *
     * <p>
     * If it's evaluating bodies (see {@link StepContext#newBodyInvoker()},
     * then it's callback needs to be invoked.
     * <p>
     * Do not use this from a step. Throw {@link FlowInterruptedException} or some other exception instead.
     *
     * @see StepExecution#stop(Throwable)
     * @see Executor#interrupt(Result)
     */
    public abstract void interrupt(Result r, CauseOfInterruption... causes) throws IOException, InterruptedException;

    public abstract void addListener(GraphListener listener);

    /**
     * Checks whether this flow execution has finished executing completely.
     */
    public boolean isComplete() {
        List<FlowNode> heads = getCurrentHeads();
        return heads.size()==1 && heads.get(0) instanceof FlowEndNode;
    }

    /**
     * If this execution {@linkplain #isComplete() has completed} with an error,
     * report that.
     *
     * This is a convenience method to look up the error result from {@ink FlowEndNode}.
     */
    public final @CheckForNull Throwable getCauseOfFailure() {
        List<FlowNode> heads = getCurrentHeads();
        if (heads.size()!=1 || !(heads.get(0) instanceof FlowEndNode))
            return null;

        FlowNode e = heads.get(0);
        ErrorAction error = e.getAction(ErrorAction.class);
        if (error==null)    return null;        // successful completion

        return error.getError();
    }

    /**
     * Loads a node by its ID.
     * Also gives each {@link FlowNode} a portion of the URL space.
     *
     * @see FlowNode#getId()
     */
    public abstract @CheckForNull FlowNode getNode(String id) throws IOException;

    /**
     * Looks up authentication associated with this flow execution.
     * For example, if a flow is configured to be a trusted agent of a user, that would be set here.
     * A flow run triggered by a user manually might be associated with the runtime, or it might not.
     * @return an authentication; {@link ACL#SYSTEM} as a fallback, or {@link Jenkins#ANONYMOUS} if the flow is supposed to be limited to a specific user but that user cannot now be looked up
     */
    public abstract @Nonnull Authentication getAuthentication();

}
