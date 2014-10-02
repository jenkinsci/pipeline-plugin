/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package org.jenkinsci.plugins.workflow.actions;

import hudson.model.Action;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

/**
 * Attached to a {@link FlowNode} to indicate that it entered a “stage” of a build.
 * This could be used by high-level visualizations which do not care about individual nodes.
 * <p>Flow nodes preceding one with this action are considered to be in no stage, or an anonymous stage.
 * This flow node, and any descendants until the next node with this action, are in the stage named here.
 * <p>Stages might be considered linear throughout a {@link FlowExecution},
 * or only within a single thread of an execution (branch of the flow graph).
 * <p>The standard implementation is attached by {@code StageStep} and is linear within an execution (threads are ignored).
 */
public interface StageAction extends Action {

    /**
     * Gets the name of the stage.
     * Intended to be unique within a given {@link FlowExecution}.
     * @return a stage identifier
     */
    String getStageName();
}