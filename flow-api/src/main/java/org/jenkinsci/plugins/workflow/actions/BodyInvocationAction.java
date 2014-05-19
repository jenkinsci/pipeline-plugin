package org.jenkinsci.plugins.workflow.actions;

import com.google.common.util.concurrent.FutureCallback;
import hudson.model.InvisibleAction;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/**
 * Attached to {@link BlockStartNode} to indicate that this block
 * represents {@linkplain StepContext#invokeBodyLater(FutureCallback, Object...) an invocation of body block}.
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
