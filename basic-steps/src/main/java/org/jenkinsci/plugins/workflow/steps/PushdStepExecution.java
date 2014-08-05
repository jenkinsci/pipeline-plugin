package org.jenkinsci.plugins.workflow.steps;

import hudson.FilePath;
import hudson.model.TaskListener;

import javax.inject.Inject;

/**
 * @author Kohsuke Kawaguchi
 */
public class PushdStepExecution extends StepExecution {
    @Inject
    transient PushdStep step;
    @StepContextParameter
    private transient TaskListener listener;
    @StepContextParameter
    private transient FilePath cwd;

    @Override
    public boolean start() throws Exception {
        FilePath dir = cwd.child(step.getValue());
        listener.getLogger().println("Running in " + dir);
        getContext().invokeBodyLater(getContext(), dir);
        return false;
    }
}
