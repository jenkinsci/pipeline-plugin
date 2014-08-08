package org.jenkinsci.plugins.workflow.steps.build;

import hudson.model.Run;
import jenkins.model.RunAction2;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/**
 * @author Vivek Pandey
 */
public class BuildTriggerAction implements RunAction2 {
    private final StepContext context;

    public BuildTriggerAction(StepContext context) {
        this.context = context;
    }

    public StepContext getStepContext(){
        return context;
    }

    @Override
    public void onAttached(Run<?, ?> r) {
    }

    @Override
    public void onLoad(Run<?, ?> r) {
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return null;
    }

}
