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

package org.jenkinsci.plugins.workflow.steps;

import hudson.model.AbstractDescribableImpl;

import java.util.Map;

/**
 * One thing that can be done, perhaps asynchronously.
 * @author Kohsuke Kawaguchi
 * @author Jesse Glick
 */
// the intent is that this is generic enough for BuildSteps interop
public abstract class Step extends AbstractDescribableImpl<Step> {
    /**
     * Start execution of something and report the end result back to the given callback.
     *
     * Arguments are passed when {@linkplain StepDescriptor#newInstance(Map) instantiating steps}.
     *
     * @return
     *      true if the execution of this step has synchronously completed before this method returns.
     *      It is the callee's responsibility to set the return value via {@link StepContext#onSuccess(Object)}
     *      or {@link StepContext#onFailure(Throwable)}.
     *
     *      false if the asynchronous execution has started and that {@link StepContext}
     *      will be notified when the result comes in. (Note that the nature of asynchrony is such that it is possible
     *      for the {@link StepContext} to be already notified before this method returns.)
     * @throws Exception
     *      if any exception is thrown, {@link Step} is assumed to have completed abnormally synchronously
     *      (as if {@link StepContext#onFailure(Throwable) is called and the method returned true.)
     */
    public abstract StepExecution start(StepContext context) throws Exception;

    @Override public StepDescriptor getDescriptor() {
        return (StepDescriptor) super.getDescriptor();
    }

}
