package org.jenkinsci.plugins.workflow.test.steps;

import hudson.FilePath;
import hudson.Util;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.io.File;

/**
 * @author Kohsuke Kawaguchi
 */
public class TmpDirStepExecution extends AbstractStepExecutionImpl {
    @Override
    public boolean start() throws Exception {
        File dir = Util.createTempDir();
        getContext().newBodyInvoker()
                .withContext(new FilePath(dir))
                .withCallback(new Callback(getContext(), dir))
                .withDisplayName(null)
                .start();
        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
    }

    /**
     * Wipe off the allocated temporary directory in the end.
     */
    private static final class Callback extends BodyExecutionCallback {
        private final StepContext context;
        private final File dir;

        Callback(StepContext context, File dir) {
            this.context = context;
            this.dir = dir;
        }

        @Override public void onSuccess(StepContext context, Object result) {
            this.context.onSuccess(result);
            delete();
        }

        @Override public void onFailure(StepContext context, Throwable t) {
            this.context.onFailure(t);
            delete();
        }

        private void delete() {
            try {
                new FilePath(dir).deleteRecursive();
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }
}
