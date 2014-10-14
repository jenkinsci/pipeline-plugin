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

import hudson.Extension;
import hudson.Util;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Executor;
import hudson.model.Label;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.QueryParameter;

/**
 * Grabs an {@link Executor} on a node of your choice and runs its block with that executor occupied.
 *
 * <p>
 * Used like:
 * <pre>
 *     node("foo") {
 *         // execute some stuff in a slave that has a label "foo" while workflow has this slave
 *     }
 * </pre>
 */
public final class ExecutorStep extends AbstractStepImpl {

    private final @CheckForNull String label;

    @DataBoundConstructor public ExecutorStep(String label) {
        this.label = Util.fixEmptyAndTrim(label);
    }
    
    public @CheckForNull String getLabel() {
        return label;
    }

    @Extension public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(ExecutorStepExecution.class);
        }

        @Override public String getFunctionName() {
            return "node";
        }

        @Override public String getDisplayName() {
            return "Allocate node";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        // pending https://trello.com/c/THjT9lwd/131-autocompletioncandidates-for-label
        public AutoCompletionCandidates doAutoCompleteLabel(@QueryParameter String value) {
            AutoCompletionCandidates c = new AutoCompletionCandidates();
            for (Label label : Jenkins.getInstance().getLabels()) {
                if (label.getName().startsWith(value)) {
                    c.add(label.getName());
                }
            }
            return c;
        }
        // proper doCheckValue also requires API requested above

    }

}
