package org.jenkinsci.plugins.workflow.test.steps;

import com.google.inject.Inject;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import java.io.File;

/**
 * @author Kohsuke Kawaguchi
 */
public class WatchYourStepExecution extends StepExecution {
    @Inject
    WatchYourStep step;

    @Override
    public boolean start() {
        if (getPath().exists()) {
            // synchronous case. Sometimes async steps can complete synchronously
            getContext().onSuccess(null);
            return true;
        }

        // asynchronous case.
        // TODO: move the persistence logic to this instance
        step.getDescriptor().addWatch(this);

        return false;
    }

    public File getPath() {
        return step.watch;
    }
}
