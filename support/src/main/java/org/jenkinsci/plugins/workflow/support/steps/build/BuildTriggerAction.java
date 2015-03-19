package org.jenkinsci.plugins.workflow.support.steps.build;

import hudson.model.InvisibleAction;
import org.jenkinsci.plugins.workflow.steps.StepContext;

public class BuildTriggerAction extends InvisibleAction {

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

}
