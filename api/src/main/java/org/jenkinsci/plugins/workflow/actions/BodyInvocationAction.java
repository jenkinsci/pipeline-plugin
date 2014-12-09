package org.jenkinsci.plugins.workflow.actions;

import hudson.model.InvisibleAction;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;

/**
 * Attached to {@link BlockStartNode} to indicate that this block
 * represents {@linkplain BodyInvoker an invocation of body block}.
 *
 * @author Kohsuke Kawaguchi
 */
public class BodyInvocationAction extends InvisibleAction implements FlowNodeAction {
    /*
     * @param stepBlock
     *      Reference to the block that signifies the enclosing block (which corresponds
     *      to the invocation of the step itself.)
     */
    public BodyInvocationAction() {
    }

    @Override
    public void onLoad(FlowNode parent) {
        // noop
    }
}
