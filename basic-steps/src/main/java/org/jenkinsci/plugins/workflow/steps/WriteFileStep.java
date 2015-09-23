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

package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;

import javax.inject.Inject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class WriteFileStep extends AbstractStepImpl {

    private final String file;
    private final String text;
    private String encoding;

    @DataBoundConstructor public WriteFileStep(String file, String text) {
        this.file = file;
        this.text = text;
    }

    public String getFile() {
        return file;
    }

    public String getText() {
        return text;
    }

    public String getEncoding() {
        return encoding;
    }

    @DataBoundSetter public void setEncoding(String encoding) {
        this.encoding = Util.fixEmptyAndTrim(encoding);
    }

    @Extension public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "writeFile";
        }

        @Override public String getDisplayName() {
            return "Write file to workspace";
        }

    }

    public static final class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        @Inject private transient WriteFileStep step;
        @StepContextParameter private transient FilePath workspace;

        @Override protected Void run() throws Exception {
            workspace.child(step.file).write(step.text, /* TODO consider specifying encoding */ null);
            return null;
        }

        private static final long serialVersionUID = 1L;

    }

}
