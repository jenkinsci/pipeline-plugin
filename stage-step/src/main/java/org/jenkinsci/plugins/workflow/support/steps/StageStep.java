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
import javax.annotation.CheckForNull;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Marks a flow build as entering a gated “stage”, like a stage in a pipeline.
 * Each job has a set of named stages, each of which acts like a semaphore with an initial permit count,
 * but with the special behavior that only one build may be waiting at any time: the newest.
 * Credit goes to @jtnord for implementing the {@code block} operator in {@code buildflow-extensions}, which inspired this.
 */
public final class StageStep extends AbstractStepImpl {

    public final String name;
    @DataBoundSetter public @CheckForNull Integer concurrency;

    @DataBoundConstructor public StageStep(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("must specify name");
        }
        this.name = name;
    }

    @Extension public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(StageStepExecution.class);
        }

        @Override public String getFunctionName() {
            return "stage";
        }

        @Override public String getDisplayName() {
            return "Stage";
        }

    }

}
