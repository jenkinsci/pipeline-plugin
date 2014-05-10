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

package org.jenkinsci.plugins.workflow.stm;

import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

/**
 * One node in the graph of the STM.
 */
public abstract class State extends AbstractDescribableImpl<State> {

    private final String name;

    protected State(String name) {
        this.name = name;
        assert Util.fixEmpty(name) != null;
    }
    
    public String getName() {
        return name;
    }

    @Override public StateDescriptor getDescriptor() {
        return (StateDescriptor) super.getDescriptor();
    }

    /**
     * Starts running this state.
     */
    public abstract FlowNode run(StepContext context, String nodeId, FlowExecution exec, FlowNode prior);

    public abstract void success(STMExecution exec, String thread, Object returnValue);

    protected static abstract class StateDescriptor extends Descriptor<State> {

        /* TODO for some reason this always gets null:
        public FormValidation doCheckName(@QueryParameter String value) {
            if (Util.fixEmpty(value) == null) {
                return FormValidation.error("Name is mandatory.");
            } else if (STMExecution.END.equals(value)) {
                return FormValidation.error("The name ‘end’ is reserved.");
            } else {
                return FormValidation.ok();
            }
        }
        */

    }

}
