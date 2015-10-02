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
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Returns the working directory path.
 *
 * Used like:
 *
 * <pre>
 * node {
 *     def x = pwd() // where is my workspace?
 * }
 * </pre>
 */
public class PwdStep extends AbstractStepImpl {
    @DataBoundConstructor public PwdStep() {}

    @Extension public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "pwd";
        }

        @Override public String getDisplayName() {
            return "Determine current directory";
        }

    }

    public static class Execution extends AbstractSynchronousStepExecution<String> {
        
        @StepContextParameter private transient FilePath cwd;

        @Override protected String run() throws Exception {
            return cwd.getRemote();
        }

        private static final long serialVersionUID = 1L;

    }

}
