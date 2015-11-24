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

package org.jenkinsci.plugins.workflow.cps.nodes;

import hudson.model.Action;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import java.util.Collections;

/**
 * {@link AtomNode} for executing {@link Step} without body closure.
 *
 * @author Kohsuke Kawaguchi
 */
public class StepAtomNode extends AtomNode implements StepNode {

    private final String descriptorId;

    // once we successfully convert descriptorId to a real instance, cache that
    private transient StepDescriptor descriptor;

    public StepAtomNode(CpsFlowExecution exec, StepDescriptor d, FlowNode parent) {
        super(exec, exec.iotaStr(), parent);
        this.descriptorId = d!=null ? d.getId() : null;

        // we use SimpleXStreamFlowNodeStorage, which uses XStream, so
        // constructor call is always for brand-new FlowNode that has not existed anywhere.
        // such nodes always have empty actions
        setActions(Collections.<Action>emptyList());
    }

    @Override public StepDescriptor getDescriptor() {
        if (descriptor == null && descriptorId != null) {
            Jenkins j = Jenkins.getInstance();
            if (j != null) {
                descriptor = (StepDescriptor) j.getDescriptor(descriptorId);
            }
        }
        return descriptor;
    }

    @Override
    protected String getTypeDisplayName() {
        StepDescriptor d = getDescriptor();
        return d!=null ? d.getDisplayName() : descriptorId;
    }

    @Override
    protected String getTypeFunctionName() {
        StepDescriptor d = getDescriptor();
        return d != null ? d.getFunctionName() : descriptorId;
    }
}
