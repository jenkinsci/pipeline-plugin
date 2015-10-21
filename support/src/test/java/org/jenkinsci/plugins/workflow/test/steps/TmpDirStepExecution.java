package org.jenkinsci.plugins.workflow.test.steps;

import hudson.FilePath;
import hudson.Util;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;

import java.io.File;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/**
 * @author Kohsuke Kawaguchi
 */
public class TmpDirStepExecution extends AbstractStepExecutionImpl {
    @Override
    public boolean start() throws Exception {
        File dir = Util.createTempDir();
        getContext().newBodyInvoker()
                .withContext(new FilePath(dir))
                .withCallback(new Callback(dir))
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
    private static final class Callback extends BodyExecutionCallback.TailCall {
        private final File dir;

        Callback(File dir) {
            this.dir = dir;
        }

        @Override protected void finished(StepContext context) throws Exception {
            new FilePath(dir).deleteRecursive();
        }
    }
}
