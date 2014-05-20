package org.jenkinsci.plugins.workflow.cps.nodes;

import hudson.model.Action;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Pairs up with {@link StepStartNode} to designate the end of a step execution
 * that has the body.
 *
 * @author Kohsuke Kawaguchi
 */
public class StepEndNode extends BlockEndNode<StepStartNode> {
    public StepEndNode(CpsFlowExecution exec, StepStartNode stepStartNode, List<FlowNode> parents) {
        super(exec, exec.iotaStr(), stepStartNode, parents);

        // we use SimpleXStreamFlowNodeStorage, which uses XStream, so
        // constructor call is always for brand-new FlowNode that has not existed anywhere.
        // such nodes always have empty actions
        setActions(Collections.<Action>emptyList());
    }

    public StepEndNode(CpsFlowExecution exec, StepStartNode stepStartNode, FlowNode... parents) {
        this(exec, stepStartNode, Arrays.asList(parents));
    }

    @Override
    protected String getTypeDisplayName() {
        return getStartNode().getStepName() +" : End";
    }
}
