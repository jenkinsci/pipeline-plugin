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
public class PauseAction implements RunAction2 {

    private final List<PauseStep> steps = new ArrayList<PauseStep>();

    private transient Run<?,?> run;

    @Override
    public void onAttached(Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        this.run = r;
        for (PauseStep step : steps) {
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

    public synchronized void add(PauseStep step) throws IOException {
        this.steps.add(step);
        run.save();
    }

    public synchronized PauseStep getStep(String id) {
        for (PauseStep step : steps) {
            if (step.getId().equals(id))
                return step;
        }
        return null;
    }

    public synchronized List<PauseStep> getSteps() {
        return new ArrayList<PauseStep>(steps);
    }

    /**
     * Called when {@link PauseStep} is completed to remove it from the active pause list.
     */
    public synchronized void remove(PauseStep step) throws IOException {
        steps.remove(step);
        run.save();
    }
}
