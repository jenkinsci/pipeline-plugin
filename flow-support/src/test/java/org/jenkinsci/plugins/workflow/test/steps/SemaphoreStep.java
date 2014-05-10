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

package org.jenkinsci.plugins.workflow.test.steps;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import java.util.Map;
import java.util.Set;

/**
 * Step that blocks until signaled.
 */
public final class SemaphoreStep extends Step {

    private StepContext context;
    private Object returnValue;
    private Throwable error;

    /** Starts running and waits for {@link #success} or {@link #failure} to be called, if they have not been already. */
    @Override public boolean start(StepContext context) throws Exception {
        if (returnValue != null) {
            context.onSuccess(returnValue);
            return true;
        } else if (error != null) {
            // TODO or should it throw that out of this method? Step.start seems underspecified.
            context.onFailure(error);
            return true;
        } else {
            this.context = context;
            return false;
        }
    }

    /** Marks the step as having successfully completed; or, if not yet started, makes it do so synchronously when started. */
    public void success(Object returnValue) {
        if (context != null) {
            context.onSuccess(returnValue);
        } else {
            this.returnValue = returnValue;
        }
    }

    /** Marks the step as having failed; or, if not yet started, makes it do so synchronously when started. */
    public void failure(Throwable error) {
        if (context != null) {
            context.onFailure(error);
        } else {
            this.error = error;
        }
    }
    
    public StepContext getContext() {
        return context;
    }

    @Override public StepDescriptor getDescriptor() {
        return new DescriptorImpl();
    }

    /* not an @Extension */ private static final class DescriptorImpl extends StepDescriptor {

        @Override public Set<Class<?>> getRequiredContext() {
            throw new UnsupportedOperationException();
        }

        @Override public String getFunctionName() {
            throw new UnsupportedOperationException();
        }

        @Override public Step newInstance(Map<String,Object> arguments) {
            throw new UnsupportedOperationException();
        }

        @Override public String getDisplayName() {
            return "Test step";
        }

    }

}
