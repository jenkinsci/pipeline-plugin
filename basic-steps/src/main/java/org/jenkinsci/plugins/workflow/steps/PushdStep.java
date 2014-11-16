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

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import javax.inject.Inject;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Set;

/**
 * Temporarily changes the working directory.
 */
public class PushdStep extends AbstractStepImpl {

    private final String path;

    @DataBoundConstructor public PushdStep(String path) {
        this.path = RelativePathValidator.validate(path);
    }

    private Object readResolve() {
        RelativePathValidator.validate(path);
        return this;
    }

    public String getPath() {
        return path;
    }

    @Extension public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "dir";
        }

        @Override public String getDisplayName() {
            return "Change Directory";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public Set<Class<?>> getProvidedContext() {
            return ImmutableSet.<Class<?>>of(FilePath.class);
        }
    }

    public static class Execution extends AbstractStepExecutionImpl {
        
        @Inject private transient PushdStep step;
        @StepContextParameter private transient TaskListener listener;
        @StepContextParameter private transient FilePath cwd;
        private BodyExecution body;

        @Override public boolean start() throws Exception {
            FilePath dir = cwd.child(step.getPath());
            listener.getLogger().println("Running in " + dir);
            body = getContext().newBodyInvoker().withContext(dir).start();
            body.addCallback(getContext());
            return false;
        }

        @Override
        public void stop(Throwable cause) throws Exception {
            if (body!=null)
                body.cancel(cause);
        }

        private static final long serialVersionUID = 1L;

    }

}
