package org.jenkinsci.plugins.workflow.support.steps.build;

import hudson.Extension;
import hudson.console.HyperlinkNote;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import java.util.logging.Logger;
import static java.util.logging.Level.WARNING;
import javax.annotation.Nonnull;

@Extension
public class BuildTriggerListener extends RunListener<Run<?,?>>{

    private static final Logger LOGGER = Logger.getLogger(BuildTriggerListener.class.getName());

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

    @Extension
    public static final class Listener extends RunListener<Run<?, ?>> {
        @Override
        public void onStarted(Run<?, ?> run, TaskListener listener) {
            Cause.UpstreamCause upstreamCause = run.getCause(Cause.UpstreamCause.class);
            if (upstreamCause != null) {
                Run upStreamRun = upstreamCause.getUpstreamRun();
                BuildTriggerAction buildTriggerAction = run.getAction(BuildTriggerAction.class);
                //Only works when upstream is still running
                if (upStreamRun.isBuilding() && buildTriggerAction != null) {
                    try {
                        TaskListener taskListener = buildTriggerAction.getStepContext().get(TaskListener.class);
                        taskListener.getLogger().println("Starting building project: " + HyperlinkNote.encodeTo('/' + run.getUrl(), run.getFullDisplayName()));
                    } catch (Exception e) {
                        LOGGER.log(WARNING, null, e);
                    }
                }
            }
        }
    }
}
