package org.jenkinsci.plugins.workflow.cps.nodes;

import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

/**
 * {@link BlockStartNode} for executing {@link Step} with body closure.
 *
 * @author Kohsuke Kawaguchi
 */
public class StepStartNode extends BlockStartNode {
    private final String stepName;

    public StepStartNode(CpsFlowExecution exec, String stepName, FlowNode parent) {
        super(exec, exec.iota(), parent);
        this.stepName = stepName;
    }

    @Override
    protected String getTypeDisplayName() {
        return stepName +" : Start";
    }

    public String getStepName() {
        return stepName;
    }
}
