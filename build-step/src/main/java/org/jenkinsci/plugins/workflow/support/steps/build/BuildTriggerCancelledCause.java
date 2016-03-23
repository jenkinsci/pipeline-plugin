package org.jenkinsci.plugins.workflow.support.steps.build;

import java.io.Serializable;
import jenkins.model.CauseOfInterruption;

/**
 * Indicates that a build is cancelled because the workflow that requested it is aborted.
 *
 * TODO: real summary.jelly
 * @author Kohsuke Kawaguchi
 */
public class BuildTriggerCancelledCause extends CauseOfInterruption {

    private static final long serialVersionUID = 1;

    private final Throwable cause;
    // TODO: capture ModelObject (such as WorkflowRun) that caused this cancellation

    public BuildTriggerCancelledCause(Throwable cause) {
        this.cause = cause;
    }

    @Override
    public String getShortDescription() {
        return "Calling Pipeline was cancelled";
    }
}
