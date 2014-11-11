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

import hudson.model.Action;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

/**
 * Action to add timestamp metadata to a {@link FlowNode}.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class TimingAction implements Action {

    // TODO perhaps add a FlowNodeViewColumn rendering <i:formatDate value="…" type="both" dateStyle="medium" timeStyle="medium"/>

    private final long startTime = System.currentTimeMillis();

    public long getStartTime() {
        return startTime;
    }

    public static long getStartTime(FlowNode flowNode) {
        TimingAction timingAction = flowNode.getAction(TimingAction.class);
        if (timingAction != null) {
            return timingAction.getStartTime();
        } else {
            return 0;
        }
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Timing";
    }

    @Override
    public String getUrlName() {
        return null;
    }
}
