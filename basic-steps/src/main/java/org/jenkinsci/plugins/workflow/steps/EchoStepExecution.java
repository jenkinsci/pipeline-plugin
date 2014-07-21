package org.jenkinsci.plugins.workflow.steps;

import hudson.model.TaskListener;

import javax.inject.Inject;

/**
 * @author Kohsuke Kawaguchi
 */
public class EchoStepExecution extends AbstractSynchronousStepExecution<Void> {
    @Inject
    private EchoStep step;

    @StepContextParameter
    private transient TaskListener listener;

    @Override
    protected Void run() throws Exception {
        listener.getLogger().println(step.message);
        return null;
    }
}
