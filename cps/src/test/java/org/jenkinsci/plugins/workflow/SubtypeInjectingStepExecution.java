package org.jenkinsci.plugins.workflow;

import hudson.model.Node;
import hudson.model.Run;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

/**
 * @author Kohsuke Kawaguchi
 */
public class SubtypeInjectingStepExecution extends AbstractSynchronousStepExecution<Void> {

    @StepContextParameter
    transient WorkflowRun r;

    @StepContextParameter
    transient Run r2;

    @StepContextParameter
    transient Jenkins n;

    @StepContextParameter
    transient Node n2;

    @StepContextParameter
    transient CpsFlowExecution f;

    @StepContextParameter
    transient FlowExecution f2;

    @Override
    protected Void run() throws Exception {
        if (r==null || n==null || f==null)  throw new AssertionError("Bzzzt");
        if (r!=r2 || n!=n2 || f!=f2)        throw new AssertionError("What!?");
        return null;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        // nothing to do here
    }
}
