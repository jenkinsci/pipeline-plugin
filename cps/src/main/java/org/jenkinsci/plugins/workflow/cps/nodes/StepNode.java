package org.jenkinsci.plugins.workflow.cps.nodes;

import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import javax.annotation.CheckForNull;

/**
 * Optional interface for {@link FlowNode} that has associated {@link StepDescriptor}
 *
 * @author Kohsuke Kawaguchi
 */
public interface StepNode {
    /**
     * Returns the descriptor for {@link Step} that produced this flow node.
     *
     * @return
     *      null for example if the descriptor that created the node has since been uninstalled.
     */
    @CheckForNull StepDescriptor getDescriptor();
}
