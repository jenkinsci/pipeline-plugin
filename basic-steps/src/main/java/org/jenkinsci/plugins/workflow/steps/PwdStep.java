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

import com.google.inject.Inject;
import hudson.Extension;
import hudson.FilePath;
import hudson.slaves.WorkspaceList;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

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

    private boolean tmp;

    @DataBoundConstructor public PwdStep() {}

    public boolean isTmp() {
        return tmp;
    }

    @DataBoundSetter public void setTmp(boolean tmp) {
        this.tmp = tmp;
    }

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

    // TODO use https://github.com/jenkinsci/jenkins/pull/2066
    private static FilePath tempDir(FilePath ws) {
        return ws.sibling(ws.getName() + System.getProperty(WorkspaceList.class.getName(), "@") + "tmp");
    }

    public static class Execution extends AbstractSynchronousStepExecution<String> {
        
        @StepContextParameter private transient FilePath cwd;
        @Inject(optional=true) private transient PwdStep step;

        @Override protected String run() throws Exception {
            return (step.isTmp() ? tempDir(cwd) : cwd).getRemote();
        }

        private static final long serialVersionUID = 1L;

    }

}
