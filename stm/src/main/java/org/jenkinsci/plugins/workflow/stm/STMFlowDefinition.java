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

import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinitionDescriptor;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import hudson.Extension;
import hudson.model.Action;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Definition of a workflow based on a state transition machine with bits of Groovy scripting.
 */
public final class STMFlowDefinition extends FlowDefinition {

    private final List<State> states;

    @DataBoundConstructor public STMFlowDefinition(List<State> states) {
        this.states = states != null ? states : new ArrayList<State>();
        // Chain together any sequential linear states which did not already do this:
        String next = STMExecution.END;
        for (int i = this.states.size() - 1; i >= 0; i--) {
            State s = this.states.get(i);
            if (s instanceof LinearState) {
                LinearState ls = (LinearState) s;
                if (ls.getNext() == null) {
                    ls.setNext(next);
                }
            }
            next = s.getName();
        }
    }

    public List<State> getStates() {
        return states;
    }

    @Override public FlowExecution create(FlowExecutionOwner handle, List<? extends Action> actions) throws IOException {
        return new STMExecution(states, handle, actions);
    }

    @Extension public static final class DescriptorImpl extends FlowDefinitionDescriptor {

        @Override public String getDisplayName() {
            return "STM Workflow";
        }

    }

}
