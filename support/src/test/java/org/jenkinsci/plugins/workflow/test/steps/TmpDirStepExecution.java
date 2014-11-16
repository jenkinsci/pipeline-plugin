package org.jenkinsci.plugins.workflow.test.steps;

import com.google.common.util.concurrent.FutureCallback;
import hudson.FilePath;
import hudson.Util;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.io.File;
import java.io.Serializable;

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
                .start();
        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
    }

    /**
     * Wipe off the allocated temporary directory in the end.
     */
    private static final class Callback implements FutureCallback<Object>, Serializable {
        private final StepContext context;
        private final File dir;

        Callback(StepContext context, File dir) {
            this.context = context;
            this.dir = dir;
        }

        @Override public void onSuccess(Object result) {
            context.onSuccess(result);
            delete();
        }

        @Override public void onFailure(Throwable t) {
            context.onFailure(t);
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
