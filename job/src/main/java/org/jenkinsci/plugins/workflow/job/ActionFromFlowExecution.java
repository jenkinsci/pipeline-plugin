package org.jenkinsci.plugins.workflow.job;

import hudson.Extension;
import hudson.model.Action;
import jenkins.model.TransientActionFactory;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Expose {@link FlowExecution#createActions()} to {@link WorkflowRun}.
 *
 * @author Kohsuke Kawaguchi
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
        Collection<? extends Action> actions = target.getExecution().createActions();
        List<Action> wrapped = new ArrayList<Action>(actions.size());
        for (Action a : actions) {
            wrapped.add(new PrefixedAction(a));
        }
        return wrapped;
    }

    /**
     * Exposes {@link Action} under {@link FlowExecution} by prefixing it.
     */
    private final class PrefixedAction implements Action {
        private final Action base;

        public PrefixedAction(Action base) {
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
