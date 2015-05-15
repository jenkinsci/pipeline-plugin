package org.jenkinsci.plugins.workflow.support.steps.build;

import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.model.Queue;
import hudson.model.queue.FoldableAction;
import java.util.List;
import org.jenkinsci.plugins.workflow.steps.StepContext;

public class BuildTriggerAction extends InvisibleAction implements FoldableAction {

    private final StepContext context;
    private final Boolean propagate;

    public BuildTriggerAction(StepContext context, boolean propagate) {
        this.context = context;
        this.propagate = propagate;
    }

    public StepContext getStepContext(){
        return context;
    }

    public boolean isPropagate() {
        return propagate != null ? propagate : /* old serialized record */ true;
    }

    @Override public void foldIntoExisting(Queue.Item item, Queue.Task owner, List<Action> otherActions) {
        item.addAction(this); // there may be >1 upstream builds (or other unrelated causes) for a single downstream build
    }

}
