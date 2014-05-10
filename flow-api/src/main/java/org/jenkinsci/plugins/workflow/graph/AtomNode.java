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

package org.jenkinsci.plugins.workflow.graph;

import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.steps.Step;

/**
 * FlowNode that has no further FlowNodes inside.
 * Might be used to run a {@link Step}, for example.
 * @author Kohsuke Kawaguchi
 * @author Jesse Glick
 */
public abstract class AtomNode extends FlowNode {
    /**
     * Transient flag that indicates if this node is currently actively executing something.
     * (As opposed to something that has finished but pending the child node creation, like
     * in when one of the fork branches has finished before the other and waiting for the join
     * node to be created.)
     *
     * This can only go from true to false. It can be only true for {@link FlowNode}s
     * that are returned from {@link FlowExecution#getCurrentHeads()}.
     */
    private transient boolean isRunning;

    protected AtomNode(FlowExecution exec, String id, boolean runningState, FlowNode... parents) {
        super(exec, id, parents);
        this.isRunning = runningState;
    }

    public void markAsCompleted() {
        isRunning = false;
    }

    public boolean isRunning() {
        return isRunning;
    }
}
