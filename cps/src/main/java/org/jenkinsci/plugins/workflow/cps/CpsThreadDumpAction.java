package org.jenkinsci.plugins.workflow.cps;

import hudson.model.Action;

/**
 * Shows thread dump for {@link CpsFlowExecution}
 *
 * @author Kohsuke Kawaguchi
 */
public final class CpsThreadDumpAction implements Action {
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
}
