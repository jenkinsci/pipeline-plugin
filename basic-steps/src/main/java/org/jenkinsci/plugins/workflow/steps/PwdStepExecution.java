package org.jenkinsci.plugins.workflow.steps;

import hudson.FilePath;

/**
 * @author Kohsuke Kawaguchi
 */
public class PwdStepExecution extends AbstractSynchronousStepExecution<String> {
    @StepContextParameter
    private transient FilePath cwd;

    @Override
    protected String run() throws Exception {
        return cwd.getRemote();
    }
}
