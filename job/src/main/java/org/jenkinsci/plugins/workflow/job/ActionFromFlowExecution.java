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
 * Expose {@link FlowExecution#createActions()} to {@link WorkflowRun}.
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
                for (Action a : createFor(taf, execution)) {
                    wrapped.add(new PrefixedAction(a));
                }
            }
        }
        return wrapped;
    }
    private <T> Collection<? extends Action> createFor(TransientActionFactory<T> taf, FlowExecution execution) {
        return taf.createFor(taf.type().cast(execution));
    }

    /**
     * Exposes {@link Action} under {@link FlowExecution} by prefixing it.
     */
    private final class PrefixedAction implements Action {
        private final Action base;

        PrefixedAction(Action base) {
            this.base = base;
        }

        @Override
        public String getIconFileName() {
            return base.getIconFileName();
        }

        @Override
        public String getDisplayName() {
            return base.getDisplayName();
        }

        @Override
        public String getUrlName() {
            String u = base.getUrlName();
            if (u!=null) {
                if (u.startsWith("/"))      return u;   // relative to context root
                if (u.contains("://"))      return u;   // absolute
                u = "execution/"+u;
            }
            return u;
        }
    }
}
