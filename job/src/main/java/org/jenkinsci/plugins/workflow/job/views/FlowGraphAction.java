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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jenkins.model.TransientActionFactory;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.visualization.table.FlowNodeViewColumn;
import org.jenkinsci.plugins.workflow.visualization.table.FlowNodeViewColumnDescriptor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * For debugging and REST API.
 */
@ExportedBean
public final class FlowGraphAction implements Action {

    public final WorkflowRun run;

    private FlowGraphAction(WorkflowRun run) {
        this.run = run;
    }

    @Override public String getIconFileName() {
        return null;
    }

    @Override public String getDisplayName() {
        return null;
    }

    @Override public String getUrlName() {
        return "flowGraph";
    }

    @Exported
    public Collection<? extends FlowNode> getNodes() {
        FlowExecution exec = run.getExecution();
        if (exec == null) {
            return Collections.emptySet();
        }
        List<FlowNode> nodes = new ArrayList<FlowNode>();
        FlowGraphWalker walker = new FlowGraphWalker(exec);
        for (FlowNode n : walker) {
            nodes.add(n);
        }
        Collections.reverse(nodes);
        return nodes;
    }

    @SuppressWarnings("deprecation")
    public List<FlowNodeViewColumn> getColumns() {
        return FlowNodeViewColumnDescriptor.getDefaultInstances();
    }

    @Extension public static final class Factory extends TransientActionFactory<WorkflowRun> {

        @Override public Class<WorkflowRun> type() {
            return WorkflowRun.class;
        }

        @Override public Collection<? extends Action> createFor(WorkflowRun run) {
            return Collections.singleton(new FlowGraphAction(run));
        }

    }

}
