package org.jenkinsci.plugins.workflow.steps;

import com.google.inject.Inject;
import hudson.AbortException;
import hudson.model.TaskListener;

/**
 * @author Kohsuke Kawaguchi
 */
public class RetryStepExecution extends AbstractStepExecutionImpl {
    @Inject(optional=true)
    private transient RetryStep step;
    private volatile BodyExecution body;

    @Override
    public boolean start() throws Exception {
        StepContext context = getContext();
        body = context.newBodyInvoker()
            .withCallback(new Callback(step.getCount()))
            .start();
        return false;   // execution is asynchronous
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        if (body!=null)
            body.cancel(cause);
    }

    private static class Callback extends BodyExecutionCallback {

        private int left;

        Callback(int count) {
            left = count;
        }

        /* Could be added, but seems unnecessary, given the message already printed in onFailure:
        @Override public void onStart(StepContext context) {
            try {
                context.get(TaskListener.class).getLogger().println(left + " tries left");
            } catch (Exception x) {
                context.onFailure(x);
            }
        }
        */

        @Override
        public void onSuccess(StepContext context, Object result) {
            context.onSuccess(result);
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            try {
                left--;
                if (left>0) {
                    TaskListener l = context.get(TaskListener.class);
                    if (t instanceof AbortException) {
                        l.error(t.getMessage());
                    } else {
                        t.printStackTrace(l.error("Execution failed"));
                    }
                    l.getLogger().println("Retrying");
                    context.newBodyInvoker().withCallback(this).start();
                } else {
                    // No need to print anything in this case, since it will be thrown up anyway.
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
