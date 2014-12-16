package org.jenkinsci.plugins.workflow.cps.steps;

import groovy.lang.Closure;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep.ParallelLabelAction;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep.ResultHandler;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

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

        ResultHandler r = new ResultHandler(cps);

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
}
