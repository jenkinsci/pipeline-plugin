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
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import hudson.Extension;
import hudson.model.DescriptorVisibilityFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A state which runs a standard single build step.
 */
public final class StepState extends LinearState {
    
    private final Step step;
    // TODO needs to have the ability to specify expand global variable names in parameters (?), and bind result to a global variable

    @DataBoundConstructor public StepState(String name, String next, Step step) {
        super(name, next);
        assert !step.getDescriptor().takesImplicitBlockArgument();
        this.step = step;
    }

    public Step getStep() {
        return step;
    }

    @Override public FlowNode run(StepContext context, String nodeId, FlowExecution exec, FlowNode prior) {
        try {
            StepExecution e = step.start(context);
            // TODO: e should be stored somewhere
            if (e.start()) {
                // TODO assert that context has gotten a return value
            }
        } catch (Exception x) {
            context.onFailure(x);
        }
        return new AtomNode(exec, nodeId, /* TODO what if synchronous? */ prior) {
            @Override protected String getTypeDisplayName() {
                return step.getDescriptor().getDisplayName();
            }
        };
    }

    @Extension public static final class DescriptorImpl extends StateDescriptor {

        @Override public String getDisplayName() {
            return "Run one step";
        }

        public Collection<StepDescriptor> getApplicableDescriptors() {
            List<StepDescriptor> r = new ArrayList<StepDescriptor>();
            for (StepDescriptor d : DescriptorVisibilityFilter.apply(null, Jenkins.getInstance().<Step,StepDescriptor>getDescriptorList(Step.class))) {
                if (!d.takesImplicitBlockArgument()) {
                    r.add(d);
                }
            }
            return r;
        }

    }

}
