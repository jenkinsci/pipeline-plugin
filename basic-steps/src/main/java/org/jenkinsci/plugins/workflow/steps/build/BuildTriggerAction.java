package org.jenkinsci.plugins.workflow.steps.build;

import hudson.model.InvisibleAction;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/**
 * @author Vivek Pandey
 */
public class BuildTriggerAction extends InvisibleAction {
    private final StepContext context;

    public BuildTriggerAction(StepContext context) {
        this.context = context;
    }

    public StepContext getStepContext(){
        return context;
    }

}
