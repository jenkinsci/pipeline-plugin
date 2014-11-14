package org.jenkinsci.plugins.workflow;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

/**
 * {@link StepExecution} that fails to serialize.
 *
 * Used to test the error recovery path of {@link WorkflowJob}.
 *
 * @author Kohsuke Kawaguchi
 */
public class PersistenceProblemStepExecution extends AbstractStepExecutionImpl {
    public final Object notSerializable = new Object();

    private Object writeReplace() {
        throw new RuntimeException("testing the forced persistence failure behaviour");
    }

    @Override
    public boolean start() throws Exception {
        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        // nothing to do here
    }
}
