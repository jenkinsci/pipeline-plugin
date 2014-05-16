package org.jenkinsci.plugins.workflow.cps.nodes;

import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import java.util.Arrays;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class StepEndNode extends BlockEndNode<StepStartNode> {
    public StepEndNode(CpsFlowExecution exec, StepStartNode stepStartNode, List<FlowNode> parents) {
        super(exec, exec.iotaStr(), stepStartNode, parents);
    }

    public StepEndNode(CpsFlowExecution exec, StepStartNode stepStartNode, FlowNode... parents) {
        this(exec, stepStartNode, Arrays.asList(parents));
    }

    @Override
    protected String getTypeDisplayName() {
        return getStartNode().getStepName() +" : End";
    }
}
