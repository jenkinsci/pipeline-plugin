/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.support.steps.stash;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

public class UnstashStep extends AbstractStepImpl {

    private final @Nonnull String name;

    @DataBoundConstructor public UnstashStep(@Nonnull String name) {
        Jenkins.checkGoodName(name);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;

        @Inject private transient UnstashStep step;
        @StepContextParameter private transient Run<?,?> build;
        @StepContextParameter private transient FilePath workspace;
        @StepContextParameter private transient TaskListener listener;

        @Override protected Void run() throws Exception {
            StashManager.unstash(build, step.name, workspace, listener);
            return null;
        }

    }

    @Extension public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "unstash";
        }

        @Override public String getDisplayName() {
            return "Restore files previously stashed";
        }

    }

}
