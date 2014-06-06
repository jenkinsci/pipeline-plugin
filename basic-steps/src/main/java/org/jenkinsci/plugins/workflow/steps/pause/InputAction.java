package org.jenkinsci.plugins.workflow.steps.pause;

import hudson.model.Run;
import jenkins.model.RunAction2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Records the pending inputs required.
 *
 * @author Kohsuke Kawaguchi
 */
public class InputAction implements RunAction2 {

    private final List<InputStep> steps = new ArrayList<InputStep>();

    private transient Run<?,?> run;

    @Override
    public void onAttached(Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        this.run = r;
        for (InputStep step : steps) {
            step.run = run;
        }
    }

    public Run<?, ?> getRun() {
        return run;
    }

    @Override
    public String getIconFileName() {
        return "help.png";
    }

    @Override
    public String getDisplayName() {
        return "Paused for Input";
    }

    @Override
    public String getUrlName() {
        return "pause";
    }

    public synchronized void add(InputStep step) throws IOException {
        this.steps.add(step);
        run.save();
    }

    public synchronized InputStep getStep(String id) {
        for (InputStep step : steps) {
            if (step.getId().equals(id))
                return step;
        }
        return null;
    }

    public synchronized List<InputStep> getSteps() {
        return new ArrayList<InputStep>(steps);
    }

    /**
     * Called when {@link InputStep} is completed to remove it from the active pause list.
     */
    public synchronized void remove(InputStep step) throws IOException {
        steps.remove(step);
        run.save();
    }
}
