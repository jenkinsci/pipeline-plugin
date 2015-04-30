package org.jenkinsci.plugins.workflow.support.steps.build;

import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import javax.annotation.Nonnull;

@Extension
public class BuildTriggerListener extends RunListener<Run<?,?>>{

    @Override
    public void onCompleted(Run<?,?> run, @Nonnull TaskListener listener) {
        for (BuildTriggerAction action : run.getActions(BuildTriggerAction.class)) {
            if (!action.isPropagate() || run.getResult() == Result.SUCCESS) {
                action.getStepContext().onSuccess(new RunWrapper(run, false));
            } else {
                action.getStepContext().onFailure(new Exception(String.valueOf(run.getResult())));
            }
        }
    }

    @Override
    public void onDeleted(Run<?,?> run) {
        for (BuildTriggerAction action : run.getActions(BuildTriggerAction.class)) {
            action.getStepContext().onFailure(new Exception(run.getBuildStatusSummary().message));
        }
    }

}
