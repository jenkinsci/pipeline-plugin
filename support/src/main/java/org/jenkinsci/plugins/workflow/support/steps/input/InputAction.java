package org.jenkinsci.plugins.workflow.support.steps.input;

import hudson.model.Run;
import jenkins.model.RunAction2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Records the pending inputs required.
 *
 * @author Kohsuke Kawaguchi
 */
public class InputAction implements RunAction2 {

    private final List<InputStepExecution> executions = new ArrayList<InputStepExecution>();

    private transient Run<?,?> run;

    @Override
    public void onAttached(Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        this.run = r;
        assert executions != null && !executions.contains(null) : executions;
        for (InputStepExecution step : executions) {
            step.run = run;
        }
    }

    public Run<?, ?> getRun() {
        return run;
    }

    @Override
    public String getIconFileName() {
        if (executions.isEmpty())    return null;
        else                    return "help.png";
    }

    @Override
    public String getDisplayName() {
        if (executions.isEmpty())    return null;
        else                    return "Paused for Input";
    }

    @Override
    public String getUrlName() {
        return "input";
    }

    public synchronized void add(@Nonnull InputStepExecution step) throws IOException {
        this.executions.add(step);
        run.save();
    }

    public synchronized InputStepExecution getExecution(String id) {
        for (InputStepExecution e : executions) {
            if (e.input.getId().equals(id))
                return e;
        }
        return null;
    }

    public synchronized List<InputStepExecution> getExecutions() {
        return new ArrayList<InputStepExecution>(executions);
    }

    /**
     * Called when {@link InputStepExecution} is completed to remove it from the active input list.
     */
    public synchronized void remove(InputStepExecution exec) throws IOException {
        executions.remove(exec);
        run.save();
    }

    /**
     * Bind steps just by their ID names.
     */
    public InputStepExecution getDynamic(String token) {
        return getExecution(token);
    }
}
