package org.jenkinsci.plugins.workflow.steps;

import hudson.model.Action;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;

import java.util.Collection;

/**
 * Additional methods exposed for workflow body execution.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class WorkflowBodyInvoker extends BodyInvoker {
    /**
     * Adds an action to {@link BlockStartNode} that indicates the beginning of a body invocation.
     */
    public abstract WorkflowBodyInvoker withStartAction(Action a);

    public WorkflowBodyInvoker withStartActions(Action... actions) {
        for (Action a : actions)
            withStartAction(a);
        return this;
    }

    public WorkflowBodyInvoker withStartActions(Collection<? extends Action> actions) {
        for (Action a : actions)
            withStartAction(a);
        return this;
    }

    // TODO: co-variant return type overrides
}
