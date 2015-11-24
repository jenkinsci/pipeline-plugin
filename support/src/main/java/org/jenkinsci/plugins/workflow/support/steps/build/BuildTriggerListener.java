package org.jenkinsci.plugins.workflow.support.steps.build;

import hudson.Extension;
import hudson.console.HyperlinkNote;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.util.logging.Logger;
import static java.util.logging.Level.WARNING;
import javax.annotation.Nonnull;

@Extension
public class BuildTriggerListener extends RunListener<Run<?,?>>{

    private static final Logger LOGGER = Logger.getLogger(BuildTriggerListener.class.getName());

    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {
        BuildTriggerAction buildTriggerAction = run.getAction(BuildTriggerAction.class);

        if (buildTriggerAction != null) {
            StepContext stepContext = buildTriggerAction.getStepContext();
            if (stepContext != null && stepContext.isReady()) {
                try {
                    TaskListener taskListener = stepContext.get(TaskListener.class);
                    taskListener.getLogger().println("Starting building project: " + HyperlinkNote.encodeTo('/' + run.getUrl(), run.getFullDisplayName()));
                } catch (Exception e) {
                    LOGGER.log(WARNING, null, e);
                }
            }
        }
    }

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