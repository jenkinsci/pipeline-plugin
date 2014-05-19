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

import hudson.util.IOUtils;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowActionStorage;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import com.google.common.util.concurrent.FutureCallback;
import hudson.model.Result;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import javax.annotation.CheckForNull;

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

    // TODO: values are snapshot in time
    public abstract List<FlowNode> getCurrentHeads();

    public abstract boolean isCurrentHead(FlowNode n);

    /**
     * Returns the URL of this {@link FlowExecution}, relative to the context root of Jenkins.
     *
     * @return
     *      String like "job/foo/32/execution/" with trailing slash but no leading slash.
     */
    public String getUrl() {
        return getOwner().getUrlOfExecution();
    }

    /**
     * Terminates the execution of a flow.
     *
     * If any computation is going on synchronously, it will be interrupted/killed/etc.
     * If it's in a suspended state waiting to be resurrected (such as waiting for
     * {@link StepContext#onSuccess(Object)}), then it just marks the workflow as done
     * with the specified status.
     *
     * <p>
     * If it's evaluating bodies (see {@link StepContext#invokeBodyLater(FutureCallback, Object...)},
     * then it's callback needs to be invoked.
     *
     * @see Step#stop(StepContext)
     */
    public abstract void finish(Result r) throws IOException, InterruptedException;

    public final void abort() throws IOException, InterruptedException {
        finish(Result.ABORTED);
    }

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
     * Mainly for giving each {@link FlowNode} a portion fo the URL space.
     *
     * @see FlowNode#getId()
     */
    public abstract @CheckForNull FlowNode getNode(String id) throws IOException;


    /**
     * Dumps the current {@link FlowNode} graph in the GraphViz dot notation.
     *
     * Primarily for diagnosing the visualization issue.
     */
    public void doDot(StaplerResponse rsp) throws IOException {
        rsp.setContentType("text/plain");
        writeDot(new PrintWriter(rsp.getWriter()));
    }

    public void doGraphViz(StaplerResponse rsp) throws IOException {
        Process p = new ProcessBuilder("dot", "-Tpng").start();
        writeDot(new PrintWriter(p.getOutputStream()));

        rsp.setContentType("image/png");
        IOUtils.copy(p.getInputStream(), rsp.getOutputStream());
    }

    private void writeDot(PrintWriter w) throws IOException {
        try {
            w.println("digraph G {");
            FlowGraphWalker walker = new FlowGraphWalker(this);
            FlowNode n;
            while ((n=walker.next())!=null) {
                for (FlowNode p : n.getParents()) {
                    w.printf("%s -> %s\n",
                            p.getId(), n.getId());
                }

                if (n instanceof BlockStartNode) {
                    BlockStartNode sn = (BlockStartNode) n;
                    w.printf("%s [shape=trapezium]\n", n.getId());
                } else
                if (n instanceof BlockEndNode) {
                    BlockEndNode sn = (BlockEndNode) n;
                    w.printf("%s [shape=invtrapezium]\n", n.getId());
                    w.printf("%s -> %s [style=dotted]\n",
                            sn.getStartNode().getId(), n.getId());
                }
            }

            w.println("}");
        } finally {
            w.close();
        }
    }
}
