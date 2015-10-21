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
import hudson.util.HttpResponses;
import hudson.util.IOUtils;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.TransientActionFactory;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Debugging-only view of the flow graph.
 */
public final class GraphVizAction implements Action {

    public final WorkflowRun run;

    private GraphVizAction(WorkflowRun run) {
        this.run = run;
    }

    @Override public String getIconFileName() {
        return null;
    }

    @Override public String getDisplayName() {
        return null;
    }

    @Override public String getUrlName() {
        return "graphViz";
    }

    /**
     * Dumps the current {@link FlowNode} graph in the GraphViz dot notation.
     *
     * Primarily for diagnosing the visualization issue.
     */
    public HttpResponse doDot() throws IOException {
        StringWriter sw = new StringWriter();
        writeDot(new PrintWriter(sw));
        return HttpResponses.plainText(sw.toString());
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings("DM_DEFAULT_ENCODING")
    public void doIndex(StaplerResponse rsp) throws IOException {
        Process p = new ProcessBuilder("dot", "-Tpng").start();
        writeDot(new PrintWriter(p.getOutputStream()));

        rsp.setContentType("image/png");
        IOUtils.copy(p.getInputStream(), rsp.getOutputStream());
    }

    private void writeDot(PrintWriter w) throws IOException {
        try {
            w.println("digraph G {");
            FlowGraphWalker walker = new FlowGraphWalker(run.getExecution());
            for (FlowNode n : walker) {
                for (FlowNode p : n.getParents()) {
                    w.printf("%s -> %s%n",
                            p.getId(), n.getId());
                }

                if (n instanceof BlockStartNode) {
                    BlockStartNode sn = (BlockStartNode) n;
                    w.printf("%s [shape=trapezium]%n", n.getId());
                } else
                if (n instanceof BlockEndNode) {
                    BlockEndNode sn = (BlockEndNode) n;
                    w.printf("%s [shape=invtrapezium]%n", n.getId());
                    w.printf("%s -> %s [style=dotted]%n",
                            sn.getStartNode().getId(), n.getId());
                }

                w.printf("%s [label=\"%s: %s\"]%n", n.getId(), n.getId(), n.getDisplayName());
            }

            w.println("}");
        } finally {
            w.close();
        }
    }

    @Extension public static final class Factory extends TransientActionFactory<WorkflowRun> {

        @Override public Class<WorkflowRun> type() {
            return WorkflowRun.class;
        }

        @Override public Collection<? extends Action> createFor(WorkflowRun run) {
            return Collections.singleton(new GraphVizAction(run));
        }

    }

}
