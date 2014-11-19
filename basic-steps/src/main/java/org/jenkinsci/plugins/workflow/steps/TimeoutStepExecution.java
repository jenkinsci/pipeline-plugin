package org.jenkinsci.plugins.workflow.steps;

import jenkins.util.Timer;

import javax.inject.Inject;
import java.util.concurrent.ScheduledFuture;

/**
 * @author Kohsuke Kawaguchi
 */
@edu.umd.cs.findbugs.annotations.SuppressWarnings("SE_INNER_CLASS")
public class TimeoutStepExecution extends AbstractStepExecutionImpl {
    @Inject
    private TimeoutStep step;
    private BodyExecution body;
    private transient ScheduledFuture<?> killer;

    @Override
    public boolean start() throws Exception {
        StepContext context = getContext();
        body = context.newBodyInvoker()
                .withCallback(new Callback())
                .withDisplayName(null)  // hide the body block
                .start();
        setupTimer();
        return false;   // execution is asynchronous
    }

    @Override
    public void onResume() {
        super.onResume();
        setupTimer();
    }

    private void setupTimer() {
        killer = Timer.get().schedule(new Runnable() {
            @Override
            public void run() {
                // TODO: print this to console
                if (!body.isDone()) {
                    body.cancel(true);
                }
            }
        }, step.getTime(), step.getUnit());
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        if (body!=null)
            body.cancel(cause);
    }

    private class Callback extends BodyExecutionCallback {
        @Override
        public void onSuccess(StepContext context, Object result) {
            cancelKiller();
            getContext().onSuccess(result);
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            cancelKiller();
            getContext().onFailure(t);
        }

        private void cancelKiller() {
            if (killer!=null) {
                killer.cancel(true);
                killer = null;
            }
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
