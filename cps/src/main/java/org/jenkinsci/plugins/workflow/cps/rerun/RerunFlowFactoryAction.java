/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.cps.rerun;

import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.model.Queue;
import java.io.IOException;
import java.util.List;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsFlowFactoryAction2;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;

/**
 * Attached to a run that is a rerun of an earlier one.
 */
class RerunFlowFactoryAction extends InvisibleAction implements CpsFlowFactoryAction2, Queue.QueueAction {

    private transient final String script;
    private transient final boolean sandbox;
    
    RerunFlowFactoryAction(String script, boolean sandbox) {
        this.script = script;
        this.sandbox = sandbox;
    }

    @Override public CpsFlowExecution create(FlowDefinition def, FlowExecutionOwner owner, List<? extends Action> actions) throws IOException {
        return new CpsFlowExecution(script, sandbox, owner);
    }

    @Override public boolean shouldSchedule(List<Action> actions) {
        return true; // do not coalesce
    }

}
