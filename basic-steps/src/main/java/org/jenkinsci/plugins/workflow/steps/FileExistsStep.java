/*
 * The MIT License
 *
 * Copyright 2015 Bastian Echterhoelter.
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

package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
import hudson.FilePath;

import javax.inject.Inject;

import org.kohsuke.stapler.DataBoundConstructor;

public final class FileExistsStep extends AbstractStepImpl {

    private final String file;

    @DataBoundConstructor public FileExistsStep(String file) {
        this.file = file;
    }

    public String getFile() {
        return file;
    }

    @Extension public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "fileExists";
        }

        @Override public String getDisplayName() {
            return "Verify if file exists in workspace";
        }

    }

    public static final class Execution extends AbstractSynchronousNonBlockingStepExecution<Boolean> {

        @Inject private transient FileExistsStep step;
        @StepContextParameter private transient FilePath workspace;

        @Override protected Boolean run() throws Exception {
        	return workspace.child(step.file).exists();
        }

        private static final long serialVersionUID = 1L;

    }

}
