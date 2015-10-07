package org.jenkinsci.plugins.workflow.cps.nodes;

import hudson.model.Action;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.actions.BodyInvocationAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import java.util.Collections;

/**
 * {@link BlockStartNode} for executing {@link Step} with body closure.
 *
 * @author Kohsuke Kawaguchi
 */
public class StepStartNode extends BlockStartNode implements StepNode {
    private final String descriptorId;

    private transient StepDescriptor descriptor;

    public StepStartNode(CpsFlowExecution exec, StepDescriptor d, FlowNode parent) {
        super(exec, exec.iotaStr(), parent);
        this.descriptor = d;
        this.descriptorId = d!=null ? d.getId() : null;

        // we use SimpleXStreamFlowNodeStorage, which uses XStream, so
        // constructor call is always for brand-new FlowNode that has not existed anywhere.
        // such nodes always have empty actions
        setActions(Collections.<Action>emptyList());
    }

    public StepDescriptor getDescriptor() {
        if (descriptor == null) {
            Jenkins j = Jenkins.getInstance();
            if (j != null) {
                descriptor = (StepDescriptor) j.getDescriptor(descriptorId);
            }
        }
        return descriptor;
    }

    @Override
    protected String getTypeDisplayName() {
        return getStepName() + (isBody() ?" : Body":"") + " : Start";
    }

    @Override
    protected String getTypeFunctionName() {
        StepDescriptor d = getDescriptor();
        if (isBody()) {
            return (d != null ? d.getFunctionName() : descriptorId) + " {";
        } else {
            return getStepName() + " : Start";
        }
    }

    public boolean isBody() {
        return getAction(BodyInvocationAction.class)!=null;
    }

    public String getStepName() {
        StepDescriptor d = getDescriptor();
        return d!=null ? d.getDisplayName() : descriptorId;
    }
}
