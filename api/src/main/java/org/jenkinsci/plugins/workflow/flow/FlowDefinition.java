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

package org.jenkinsci.plugins.workflow.flow;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Action;
import java.io.IOException;
import java.util.List;

/**
 * Actual executable script.
 *
 * <strike>Extension point for CPS and other engines.</strike>
 *
 * @author Kohsuke Kawaguchi
 * @author Jesse Glick
 */
public abstract class FlowDefinition extends AbstractDescribableImpl<FlowDefinition> {
    /**
     * Starts a brand new execution of this definition from the beginning.
     *
     * @param actions
     *      Additional parameters to how
     */
    public abstract FlowExecution create(FlowExecutionOwner handle, List<? extends Action> actions) throws IOException;

    @Override public FlowDefinitionDescriptor getDescriptor() {
        return (FlowDefinitionDescriptor) super.getDescriptor();
    }

}
