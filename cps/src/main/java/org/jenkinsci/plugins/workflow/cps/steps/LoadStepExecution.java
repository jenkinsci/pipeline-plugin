package org.jenkinsci.plugins.workflow.cps.steps;

import com.google.inject.Inject;
import groovy.lang.GroovyShell;
import hudson.FilePath;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

/**
* @author Kohsuke Kawaguchi
*/
public class LoadStepExecution extends StepExecution {
    @StepContextParameter
    private transient FilePath cwd;

    @Inject
    private LoadStep step;

    @Override
    public boolean start() throws Exception {
        GroovyShell shell = CpsThread.current().getExecution().getShell();

        // this might throw CpsCallableInvocation to trigger async execution
        // TODO in that case what happens to the return value?
        Object o = shell.evaluate(cwd.child(step.getPath()).readToString());

        getContext().onSuccess(o);
        return true;
    }

    @Override
    public void stop() throws Exception {
        // TODO is there a test confirming that this gets passed on to the running step inside the evaluated script?
    }
}
