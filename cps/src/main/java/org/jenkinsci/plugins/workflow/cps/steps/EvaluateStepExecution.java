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
public class EvaluateStepExecution extends StepExecution {
    @StepContextParameter
    private transient FilePath cwd;

    @Inject
    private EvaluateStep step;

    @Override
    public boolean start() throws Exception {
        GroovyShell shell = CpsThread.current().getExecution().getShell();

        // this might throw CpsCallableInvocation to trigger async execution
        Object o = shell.evaluate(cwd.child(step.path).readToString());

        getContext().onSuccess(o);
        return true;
    }

    @Override
    public void stop() throws Exception {
    }
}
