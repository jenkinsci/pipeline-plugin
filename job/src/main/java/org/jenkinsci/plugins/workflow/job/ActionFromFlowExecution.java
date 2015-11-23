package org.jenkinsci.plugins.workflow.job;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Action;
import jenkins.model.TransientActionFactory;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Expose {@link FlowExecution} actions to {@link WorkflowRun}.
 */
@Extension
public class ActionFromFlowExecution extends TransientActionFactory<WorkflowRun> {
    @Override
    public Class<WorkflowRun> type() {
        return WorkflowRun.class;
    }

    @Nonnull
    @Override
    public Collection<? extends Action> createFor(WorkflowRun target) {
        FlowExecution execution = target.getExecution();
        List<Action> wrapped = new ArrayList<Action>();
        for (TransientActionFactory<?> taf : ExtensionList.lookup(TransientActionFactory.class)) {
            if (taf.type().isInstance(execution)) {
                wrapped.addAll(createFor(taf, execution));
            }
        }
        return wrapped;
    }
    private <T> Collection<? extends Action> createFor(TransientActionFactory<T> taf, FlowExecution execution) {
        return taf.createFor(taf.type().cast(execution));
    }

}
