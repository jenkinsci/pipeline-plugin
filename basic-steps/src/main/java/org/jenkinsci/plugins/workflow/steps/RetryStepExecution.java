package org.jenkinsci.plugins.workflow.steps;

import com.google.common.util.concurrent.FutureCallback;

import javax.inject.Inject;
import java.io.Serializable;

/**
 * @author Kohsuke Kawaguchi
 */
public class RetryStepExecution extends AbstractStepExecutionImpl {
    @Inject
    private transient RetryStep step;
    private volatile BodyExecution body;

    @Override
    public boolean start() throws Exception {
        StepContext context = getContext();
        body = context.newBodyInvoker()
            .withCallback(new Callback(context, step.getCount()))
            .start();
        return false;   // execution is asynchronous
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        if (body!=null)
            body.cancel(cause);
    }

    private static class Callback implements FutureCallback<Object>, Serializable {

        private final StepContext context;
        private int left;

        Callback(StepContext context, int count) {
            this.context = context;
            left = count;
        }

        @Override
        public void onSuccess(Object result) {
            context.onSuccess(result);
        }

        @Override
        public void onFailure(Throwable t) {
            try {
                // TODO: here we want to access TaskListener that belongs to the body invocation end node.
                // how should we do that?
                /* TODO not currently legal:
                TaskListener l = getContext().get(TaskListener.class);
                t.printStackTrace(l.error("Execution failed"));
                */
                left--;
                if (left>0) {
                    /*
                    l.getLogger().println("Retrying");
                    */
                    context.newBodyInvoker().withCallback(this).start();
                } else {
                    context.onFailure(t);
                }
            } catch (Throwable p) {
                context.onFailure(p);
            }
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
