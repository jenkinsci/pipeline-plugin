package org.jenkinsci.plugins.workflow.cps.steps;

import groovy.lang.Closure;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep.ResultHandler;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.FLOW_NODE;

/**
 * {@link StepExecution} for {@link ParallelStep}.
 *
 * @author Kohsuke Kawaguchi
 */
class ParallelStepExecution extends StepExecution {
    private transient ParallelStep parallelStep;

    private final List<BodyExecution> bodies = new ArrayList<BodyExecution>();

    public ParallelStepExecution(ParallelStep parallelStep, StepContext context) {
        super(context);
        this.parallelStep = parallelStep;
    }


    @Override
    public boolean start() throws Exception {
        CpsStepContext cps = (CpsStepContext) getContext();
        CpsThread t = CpsThread.current();

        ResultHandler r = new ResultHandler(cps, this, parallelStep.isFailFast());

        for (Entry<String,Closure> e : parallelStep.closures.entrySet()) {
            BodyExecution body = cps.newBodyInvoker(t.getGroup().export(e.getValue()))
                    .withStartAction(new ParallelLabelAction(e.getKey()))
                    .withCallback(r.callbackFor(e.getKey()))
                    .start();
            bodies.add(body);
        }

        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        for (BodyExecution body : bodies) {
            body.cancel(cause);
        }
    }

    private static final long serialVersionUID = 1L;

    @PersistIn(FLOW_NODE)
    private static class ParallelLabelAction extends LabelAction implements ThreadNameAction {
        private final String branchName;

        ParallelLabelAction(String branchName) {
            super(null);
            this.branchName = branchName;
        }

        @Override
        public String getDisplayName() {
            return "Parallel branch: "+branchName;
        }

        @Nonnull
        @Override
        public String getThreadName() {
            return branchName;
        }
    }
}
