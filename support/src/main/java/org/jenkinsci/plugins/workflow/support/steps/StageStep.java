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

package org.jenkinsci.plugins.workflow.support.steps;

import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import org.jenkinsci.plugins.workflow.actions.StageAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Marks a flow build as entering a gated “stage”, like a stage in a pipeline.
 * Each job has a set of named stages, each of which acts like a semaphore with an initial permit count,
 * but with the special behavior that only one build may be waiting at any time: the newest.
 * Credit goes to @jtnord for implementing the {@code block} operator in {@code buildflow-extensions}, which inspired this.
 */
public class StageStep extends Step {

    public final String name;
    protected final @CheckForNull Integer concurrency;

    private StageStep(String name, @CheckForNull Integer concurrency) {
        if (name == null) {
            throw new IllegalArgumentException("must specify name");
        }
        this.name = name;
        this.concurrency = concurrency;
    }

    @DataBoundConstructor public StageStep(String name, String concurrency) {
        this(Util.fixEmpty(name), Util.fixEmpty(concurrency) != null ? Integer.parseInt(concurrency) : null);
    }

    public String getConcurrency() {
        return concurrency == null ? "" : Integer.toString(concurrency);
    }

    @Override public StageStepExecution start(StepContext context) throws Exception {
        FlowNode flownode = context.get(FlowNode.class);
        if (flownode != null) {
            flownode.addAction(new StageAction().setStageName(name));
        }
        return new StageStepExecution(this,context);
    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public Set<Class<?>> getRequiredContext() {
            Set<Class<?>> r = new HashSet<Class<?>>();
            r.add(Run.class);
            r.add(FlowExecution.class);
            r.add(FlowNode.class);
            r.add(TaskListener.class);
            return r;
        }

        @Override public String getFunctionName() {
            return "stage";
        }

        @Override public Step newInstance(Map<String,Object> arguments) {
            return new StageStep((String) arguments.get("value"), (Integer) arguments.get("concurrency"));
        }

        @Override public String getDisplayName() {
            return "Stage";
        }

    }

}
