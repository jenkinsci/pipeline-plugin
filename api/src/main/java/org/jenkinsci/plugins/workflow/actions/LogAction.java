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

package org.jenkinsci.plugins.workflow.actions;

import hudson.console.AnnotatedLargeText;
import hudson.model.Action;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/**
 * Associated with a node which has some log text.
 * So if {@link StepContext} is asked for {@link TaskListener}, the {@link FlowExecution} should attach this action to the contextual {@link FlowNode}.
 */
public abstract class LogAction implements Action {
    /**
     * Access the log file and expose it to the UI with the progressive logging functionality
     */
    public abstract AnnotatedLargeText<? extends FlowNode> getLogText();

    @Override
    public String getIconFileName() {
        return "clipboard.png";
    }

    @Override
    public String getDisplayName() {
        return "Console Output";
    }

    @Override
    public String getUrlName() {
        return "log";
    }
}
