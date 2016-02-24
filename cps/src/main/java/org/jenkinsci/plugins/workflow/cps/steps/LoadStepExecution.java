package org.jenkinsci.plugins.workflow.cps.steps;

import com.google.inject.Inject;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.TaskListener;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Loads another Groovy script file and executes it.
 *
 * @author Kohsuke Kawaguchi
 */
public class LoadStepExecution extends AbstractStepExecutionImpl {
    @StepContextParameter
    private transient FilePath cwd;

    @Inject(optional=true)
    private transient LoadStep step;

    @StepContextParameter
    private transient FlowNode node;

    @StepContextParameter
    private transient TaskListener listener;

    @Override
    public boolean start() throws Exception {
        CpsStepContext cps = (CpsStepContext) getContext();
        CpsThread t = CpsThread.current();

        CpsFlowExecution execution = t.getExecution();

        String text = cwd.child(step.getPath()).readToString();
        for (Replacer replacer : ExtensionList.lookup(Replacer.class)) {
            text = replacer.replace(text, execution, execution.getNextScriptName(step.getPath()), listener);
        }

        Script script = execution.getShell().parse(text);

        node.addAction(new LabelAction(step.getPath()));

        // execute body as another thread that shares the same head as this thread
        // as the body can pause.
        cps.newBodyInvoker(t.getGroup().export(script))
                .withCallback(BodyExecutionCallback.wrap(cps))
                .start(); // when the body is done, the load step is done

        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        // noop
        //
        // the head of the CPS thread that's executing the body should stop and that's all we need to do.
    }

    private static final long serialVersionUID = 1L;

    /** Allows content of the loaded script to be substituted. */
    @Restricted(NoExternalUse.class) // for now anyway
    public interface Replacer extends ExtensionPoint {

        /**
         * Replaces some loaded script text with something else.
         * @param text the original text (or that processed by an earlier replacer)
         * @param execution the associated execution
         * @param clazz the expected Groovy class name to be produced, like {@code Script1}
         * @param listener a way to note any issues
         * @return possibly edited text
         */
        @Nonnull String replace(@Nonnull String text, @Nonnull CpsFlowExecution execution, @Nonnull String clazz, @Nonnull TaskListener listener);

    }

}
