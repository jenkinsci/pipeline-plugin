package org.jenkinsci.plugins.workflow.steps;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jenkins.model.CauseOfInterruption;
import jenkins.util.Timer;

/**
 * @author Kohsuke Kawaguchi
 */
@SuppressFBWarnings("SE_INNER_CLASS")
public class TimeoutStepExecution extends AbstractStepExecutionImpl {
    @Inject(optional=true)
    private transient TimeoutStep step;
    private BodyExecution body;
    private transient ScheduledFuture<?> killer;

    private long end = 0;

    @Override
    public boolean start() throws Exception {
        StepContext context = getContext();
        body = context.newBodyInvoker()
                .withCallback(new Callback())
                .withDisplayName(null)  // hide the body block
                .start();
        long now = System.currentTimeMillis();
        end = now + step.getUnit().toMillis(step.getTime());
        setupTimer(now);
        return false;   // execution is asynchronous
    }

    @Override
    public void onResume() {
        super.onResume();
        setupTimer(System.currentTimeMillis());
    }

    /**
     * Sets the timer to manage the timeout.
     *
     * @param now Current time in milliseconds.
     */
    private void setupTimer(final long now) {
        if (end > now) {
            killer = Timer.get().schedule(new Runnable() {
                @Override
                public void run() {
                    body.cancel(new ExceededTimeout());
                }
            }, end - now, TimeUnit.MILLISECONDS);
        } else {
            body.cancel(new ExceededTimeout());
        }
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        if (body!=null)
            body.cancel(cause);
    }

    private class Callback extends BodyExecutionCallback.TailCall {

        @Override protected void finished(StepContext context) throws Exception {
            if (killer!=null) {
                killer.cancel(true);
                killer = null;
            }
        }

        private static final long serialVersionUID = 1L;

    }

    /**
     * Common cause in this step.
     */
    public static final class ExceededTimeout extends CauseOfInterruption {

        private static final long serialVersionUID = 1L;

        @Override
        public String getShortDescription() {
            return "Timeout has been exceeded";
        }
    }

    private static final long serialVersionUID = 1L;
}
