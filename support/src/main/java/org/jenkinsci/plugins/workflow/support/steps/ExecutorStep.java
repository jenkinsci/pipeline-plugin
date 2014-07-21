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

package org.jenkinsci.plugins.workflow.support.steps;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Executor;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Grabs an {@link Executor} on a node of your choice and runs its block with that executor occupied.
 */
public final class ExecutorStep extends Step {

    private final String label;

    @DataBoundConstructor public ExecutorStep(String label) {
        this.label = label;
    }
    
    public String getLabel() {
        return label;
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new ExecutorStepExecution(this);
    }


    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public Set<Class<?>> getRequiredContext() {
            Set<Class<?>> r = new HashSet<Class<?>>();
            // FlowExecution useful but currently not required
            r.add(TaskListener.class);
            r.add(EnvVars.class);
            r.add(Run.class);
            return r;
        }

        @Override public String getFunctionName() {
            return "node";
        }

        @Override public Step newInstance(Map<String,Object> arguments) {
            return new ExecutorStep((String) arguments.get("value"));
        }

        @Override public String getDisplayName() {
            return "Allocate node";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        // TODO completion on label field (cf. AbstractProjectDescriptor)

    }

}
