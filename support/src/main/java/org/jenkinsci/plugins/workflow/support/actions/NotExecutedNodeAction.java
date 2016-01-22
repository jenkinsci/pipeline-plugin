package org.jenkinsci.plugins.workflow.support.actions;

import hudson.model.InvisibleAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

/**
 * Action that marks a node as being non-executed.
 * <p>
 *       Available so that views of a job flow node graph can be properly
 *       rendered i.e. the steps that were not executed are visibly muted in some way.
 * </p>
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class NotExecutedNodeAction extends InvisibleAction {

    public static boolean isExecuted(FlowNode node) {
        return (node.getAction(NotExecutedNodeAction.class) == null);
    }
}
