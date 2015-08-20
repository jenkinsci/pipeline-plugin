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

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;

import hudson.model.Describable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * One thing that can be done, perhaps asynchronously.
 * A {@link Step} is merely the definition of how this task is configured;
 * {@link StepExecution} represents any state associated with one actual run of it.
 * <p>
 * Extends from {@link Describable} to support UI-based instantiation.
 * Your step should have a {@code config.jelly} allowing a user to configure its properties,
 * and may have a {@code help.html} and/or {@code help-fieldName.html},
 * plus {@code doEtc} methods on the {@link StepDescriptor} for form validation, completion, and so on.
 * It should have a {@link DataBoundConstructor} specifying mandatory properties.
 * It may also use {@link DataBoundSetter} for optional properties.
 * All properties also need public getters (or to be public fields) for data binding to work.
 */
public abstract class Step extends AbstractDescribableImpl<Step> implements ExtensionPoint {
    /**
     * Start execution of something and report the end result back to the given callback.
     *
     * Arguments are passed when {@linkplain StepDescriptor#newInstance instantiating steps}.
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
     *      (as if {@link StepContext#onFailure} is called and the method returned true.)
     */
    public abstract StepExecution start(StepContext context) throws Exception;

    @Override public StepDescriptor getDescriptor() {
        return (StepDescriptor) super.getDescriptor();
    }

}
