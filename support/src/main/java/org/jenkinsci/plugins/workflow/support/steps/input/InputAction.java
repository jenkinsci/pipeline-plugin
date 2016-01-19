package org.jenkinsci.plugins.workflow.support.steps.input;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Run;
import jenkins.model.RunAction2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionList;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

/**
 * Records the pending inputs required.
 */
public class InputAction implements RunAction2 {

    private static final Logger LOGGER = Logger.getLogger(InputAction.class.getName());

    private transient List<InputStepExecution> executions = new ArrayList<InputStepExecution>();
    @SuppressFBWarnings(value="IS2_INCONSISTENT_SYNC", justification="CopyOnWriteArrayList")
    private List<String> ids = new CopyOnWriteArrayList<String>();

    private transient Run<?,?> run;

    @Override
    public void onAttached(Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        this.run = r;
        synchronized (this) {
            if (ids == null) {
                // Loading from before JENKINS-25889 fix. Load the IDs and discard the executions, which lack state anyway.
                assert executions != null && !executions.contains(null) : executions;
                ids = new ArrayList<String>();
                for (InputStepExecution execution : executions) {
                    ids.add(execution.getId());
                }
                executions = null;
            }
        }
    }

    private synchronized void loadExecutions() {
        if (executions == null) {
            executions = new ArrayList<InputStepExecution>();
            try {
            FlowExecution execution = null;
            for (FlowExecution _execution : FlowExecutionList.get()) {
                if (_execution.getOwner().getExecutable() == run) {
                    execution = _execution;
                    break;
                }
            }
            if (execution != null) {
                // Futures.addCallback is the safer way to iterate, but we need to know that the result is in.
                // And we cannot start the calculation during onLoad because we are still inside WorkflowRun.onLoad and the CpsFlowExecution is not yet initialized.
                // WorkflowRun has getExecutionPromise but that is not accessible via API.
                // Even if we add such an API to FlowExecutionOwner, and look for FlowExecutionOwner.Executable,
                // FlowExecutionList would need an API for getting owners without blocking on the execution.
                for (StepExecution se : execution.getCurrentExecutions(true).get()) {
                    if (se instanceof InputStepExecution) {
                        InputStepExecution ise = (InputStepExecution) se;
                        if (ids.contains(ise.getId())) {
                            executions.add(ise);
                        }
                    }
                }
                if (executions.size() < ids.size()) {
                    LOGGER.log(Level.WARNING, "some input IDs not restored from {0}", run);
                }
            } else {
                LOGGER.log(Level.WARNING, "no flow execution found for {0}", run);
            }
            } catch (Exception x) {
                LOGGER.log(Level.WARNING, null, x);
            }
        }
    }

    public Run<?, ?> getRun() {
        return run;
    }

    @Override
    public String getIconFileName() {
        if (ids == null || ids.isEmpty()) {
            return null;
        } else {
            return "help.png";
        }
    }

    @Override
    public String getDisplayName() {
        if (ids == null || ids.isEmpty()) {
            return null;
        } else {
            return "Paused for Input";
        }
    }

    @Override
    public String getUrlName() {
        return "input";
    }

    public synchronized void add(@Nonnull InputStepExecution step) throws IOException {
        loadExecutions();
        this.executions.add(step);
        ids.add(step.getId());
        run.save();
    }

    public synchronized InputStepExecution getExecution(String id) {
        loadExecutions();
        for (InputStepExecution e : executions) {
            if (e.input.getId().equals(id))
                return e;
        }
        return null;
    }

    public synchronized List<InputStepExecution> getExecutions() {
        loadExecutions();
        return new ArrayList<InputStepExecution>(executions);
    }

    /**
     * Called when {@link InputStepExecution} is completed to remove it from the active input list.
     */
    public synchronized void remove(InputStepExecution exec) throws IOException {
        loadExecutions();
        executions.remove(exec);
        ids.remove(exec.getId());
        run.save();
    }

    /**
     * Bind steps just by their ID names.
     */
    public InputStepExecution getDynamic(String token) {
        return getExecution(token);
    }
}
