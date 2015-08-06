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

package org.jenkinsci.plugins.workflow.job.views;

import hudson.Extension;
import hudson.model.Action;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.TransientActionFactory;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;

public final class FlowGraphTableAction implements Action {

    public final WorkflowRun run;

    private FlowGraphTableAction(WorkflowRun run) {
        this.run = run;
    }

    @Override public String getIconFileName() {
        return "gear.png";
    }

    @Override public String getDisplayName() {
        return "Workflow Steps";
    }

    @Override public String getUrlName() {
        return "flowGraphTable";
    }

    public FlowGraphTable getFlowGraph() {
        FlowGraphTable t = new FlowGraphTable(run.getExecution());
        t.build();
        return t;
    }

    @Extension public static final class Factory extends TransientActionFactory<WorkflowRun> {

        @Override public Class<WorkflowRun> type() {
            return WorkflowRun.class;
        }

        @Override public Collection<? extends Action> createFor(WorkflowRun run) {
            return Collections.singleton(new FlowGraphTableAction(run));
        }

    }

}
