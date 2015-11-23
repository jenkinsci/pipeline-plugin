package org.jenkinsci.plugins.workflow.cps;

import hudson.Extension;
import hudson.model.Action;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.TransientActionFactory;

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
    
    public CpsFlowExecution getExecution() {
        return execution;
    }

    @Extension public static class Factory extends TransientActionFactory<CpsFlowExecution> {

        @Override public Class<CpsFlowExecution> type() {
            return CpsFlowExecution.class;
        }

        @Override public Collection<? extends Action> createFor(CpsFlowExecution execution) {
            return Collections.singleton(new CpsThreadDumpAction(execution));
        }

    }

}
