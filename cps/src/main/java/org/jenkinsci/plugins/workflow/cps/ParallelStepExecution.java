package org.jenkinsci.plugins.workflow.cps;

import groovy.lang.Closure;
import org.jenkinsci.plugins.workflow.cps.ParallelStep.ParallelLabelAction;
import org.jenkinsci.plugins.workflow.cps.ParallelStep.ResultHandler;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author Kohsuke Kawaguchi
 */
class ParallelStepExecution extends StepExecution {
    private transient ParallelStep parallelStep;

    public ParallelStepExecution(ParallelStep parallelStep, StepContext context) {
        super(context);
        this.parallelStep = parallelStep;
    }

    @Override
    public boolean start() throws Exception {

        // TODO: we need to take over the flow node creation for a single StepStart/End pair that wraps
        // around all the subflows (and not let DSL.invokeMethod creates AtomNode)
        // see the corresponding hack in DSL.invokeMethod

        CpsStepContext cps = (CpsStepContext) context;
        CpsThread t = CpsThread.current();

        ResultHandler r = new ResultHandler(context);

        for (Entry<String,Closure> e : parallelStep.closures.entrySet()) {
            cps.invokeBodyLater(
                    t.group.export(e.getValue()),
                    r.callbackFor(e.getKey()),
                    Collections.singletonList(new ParallelLabelAction(e.getKey()))
            );
        }

        return false;
    }

    private static final long serialVersionUID = 1L;
}
