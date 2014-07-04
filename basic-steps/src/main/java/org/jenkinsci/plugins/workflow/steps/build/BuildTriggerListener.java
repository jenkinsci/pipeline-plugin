package org.jenkinsci.plugins.workflow.steps.build;

import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import javax.annotation.Nonnull;

/**
 * @author Vivek Pandey
 */
@Extension
public class BuildTriggerListener extends RunListener<Run<?,?>>{

    @Override
    public void onCompleted(Run run, @Nonnull TaskListener listener) {
        BuildTriggerAction action = run.getAction(BuildTriggerAction.class);
        if(action != null) {
            if(run.getResult() == Result.SUCCESS) {
                action.getStepContext().onSuccess(run.getResult());
            }else{
                action.getStepContext().onFailure(new Exception(run.getResult().toString()));
            }
        }
    }

    @Override
    public void onDeleted(Run run) {
        BuildTriggerAction action = run.getAction(BuildTriggerAction.class);
        if(action != null) {
            action.getStepContext().onFailure(new Exception(run.getBuildStatusSummary().message));
        }
    }

}
