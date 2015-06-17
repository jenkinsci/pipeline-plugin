/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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
import javax.inject.Inject;
import org.kohsuke.stapler.DataBoundConstructor;

public class LabelStep extends AbstractStepImpl {

    private final String name;

    @DataBoundConstructor public LabelStep(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static class Execution extends AbstractStepExecutionImpl {

        private static final long serialVersionUID = 1L;
        
        @Inject private transient LabelStep step;

        @Override public boolean start() throws Exception {
            getContext().newBodyInvoker().withCallback(BodyExecutionCallback.wrap(getContext())).withDisplayName(step.name).start();
            return false;
        }

        @Override public void stop(Throwable cause) throws Exception {
            // should not be called
        }

    }

    @Extension public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "label";
        }

        @Override public String getDisplayName() {
            return "Labeled block";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

    }

}
