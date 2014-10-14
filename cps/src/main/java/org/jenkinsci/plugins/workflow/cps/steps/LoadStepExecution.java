package org.jenkinsci.plugins.workflow.cps.steps;

import com.google.inject.Inject;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import hudson.FilePath;
import hudson.model.Action;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import java.util.Collections;

/**
 * Loads another Groovy script file and executes it.
 *
 * @author Kohsuke Kawaguchi
 */
public class LoadStepExecution extends StepExecution {
    @StepContextParameter
    private transient FilePath cwd;

    @Inject
    private transient LoadStep step;

    @StepContextParameter
    private transient FlowNode node;

    @Override
    public boolean start() throws Exception {
        CpsStepContext cps = (CpsStepContext) getContext();
        CpsThread t = CpsThread.current();

        GroovyShell shell = CpsThread.current().getExecution().getShell();

        Script script = shell.parse(cwd.child(step.getPath()).readToString());

        node.addAction(new LabelAction("Loaded script: "+step.getPath()));

        // execute body as another thread that shares the same head as this thread
        // as the body can pause.
        cps.invokeBodyLater(
                t.getGroup().export(script),
                cps, // when the body is done, the load step is done
                Collections.<Action>emptyList()
        );

        return false;
    }

    @Override
    public void stop() throws Exception {
        // noop
        //
        // the head of the CPS thread that's executing the body should stop and that's all we need to do.
    }
}
