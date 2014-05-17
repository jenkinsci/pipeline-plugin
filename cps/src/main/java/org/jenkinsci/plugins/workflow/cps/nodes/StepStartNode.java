package org.jenkinsci.plugins.workflow.cps.nodes;

import hudson.model.Action;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;

import java.util.Collections;

/**
 * {@link BlockStartNode} for executing {@link Step} with body closure.
 *
 * @author Kohsuke Kawaguchi
 */
public class StepStartNode extends BlockStartNode {
    private final String stepName;

    public StepStartNode(CpsFlowExecution exec, String stepName, FlowNode parent) {
        super(exec, exec.iotaStr(), parent);
        this.stepName = stepName;

        // we use SimpleXStreamFlowNodeStorage, which uses XStream, so
        // constructor call is always for brand-new FlowNode that has not existed anywhere.
        // such nodes always have empty actions
        setActions(Collections.<Action>emptyList());
    }

    @Override
    protected String getTypeDisplayName() {
        return stepName +" : Start";
    }

    public String getStepName() {
        return stepName;
    }
}
