package org.jenkinsci.plugins.workflow.cps;

import hudson.Extension;
import hudson.model.Action;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import jenkins.model.TransientActionFactory;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;

/**
 * Shows thread dump for {@link CpsFlowExecution}.
 */
public final class CpsThreadDumpAction implements Action {

    private final CpsFlowExecution execution;

    private CpsThreadDumpAction(CpsFlowExecution execution) {
        this.execution = execution;
    }

    @Override
    public String getIconFileName() {
        return "gear.png";
    }

    @Override
    public String getDisplayName() {
        return "Thread Dump";
    }

    @Override
    public String getUrlName() {
        return "threadDump";
    }
    
    public String getParentUrl() throws IOException {
        return execution.getOwner().getUrl();
    }

    /* for tests */ CpsThreadDump threadDumpSynchonous() throws InterruptedException, ExecutionException {
        execution.waitForSuspension();
        return execution.getThreadDump();
    }

    public String getThreadDump() {
        return execution.getThreadDump().toString();
    }

    @Extension public static class Factory extends TransientActionFactory<FlowExecutionOwner.Executable> {

        @Override public Class<FlowExecutionOwner.Executable> type() {
            return FlowExecutionOwner.Executable.class;
        }

        @Override public Collection<? extends Action> createFor(FlowExecutionOwner.Executable executable) {
            FlowExecutionOwner owner = executable.asFlowExecutionOwner();
            if (owner != null) {
                FlowExecution exec = owner.getOrNull();
                if (exec instanceof CpsFlowExecution) {
                    return Collections.singleton(new CpsThreadDumpAction((CpsFlowExecution) exec));
                }
            }
            return Collections.emptySet();
        }

    }

}
