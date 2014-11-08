package org.jenkinsci.plugins.workflow.cps.steps;

import edu.umd.cs.findbugs.annotations.*;
import groovy.lang.Closure;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep.ParallelLabelAction;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep.ResultHandler;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import java.util.Collections;
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

    @edu.umd.cs.findbugs.annotations.SuppressWarnings("NP_NULL_ON_SOME_PATH")
    @Override
    public StepDescriptor getStepDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(ParallelStep.DescriptorImpl.class);
    }

    @Override
    public boolean start() throws Exception {

        // TODO: we need to take over the flow node creation for a single StepStart/End pair that wraps
        // around all the subflows (and not let DSL.invokeMethod creates AtomNode)
        // see the corresponding hack in DSL.invokeMethod

        CpsStepContext cps = (CpsStepContext) getContext();
        CpsThread t = CpsThread.current();

        ResultHandler r = new ResultHandler(cps);

        for (Entry<String,Closure> e : parallelStep.closures.entrySet()) {
            cps.invokeBodyLater(
                    t.getGroup().export(e.getValue()),
                    Collections.singletonList(new ParallelLabelAction(e.getKey()))
            ).addCallback(r.callbackFor(e.getKey()));
        }

        return false;
    }

    @Override
    public void stop() throws Exception {
        // TODO: see RetyrStepExecution.stop()
        throw new UnsupportedOperationException();
    }

    private static final long serialVersionUID = 1L;
}
