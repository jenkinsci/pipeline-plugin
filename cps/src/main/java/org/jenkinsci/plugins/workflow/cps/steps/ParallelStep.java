package org.jenkinsci.plugins.workflow.cps.steps;

import com.cloudbees.groovy.cps.Outcome;
import groovy.lang.Closure;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.cps.CpsVmThreadOnly;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;

/**
 * CPS-specific {@link Step} implementation that executes multiple closures in parallel.
 *
 * TODO: somehow needs to declare that this only works with CpsFlowExecution.
 *
 * @author Kohsuke Kawaguchi
 */
public class ParallelStep extends Step {

    /** should a failure in a parallel branch early terminate other branches. */
    private final boolean failFast;

    /**
     * All the sub-workflows as {@link Closure}s, keyed by their names.
     */
    /*package*/ final Map<String,Closure> closures;

    public ParallelStep(Map<String,Closure> closures, boolean failFast) {
        this.closures = closures;
        this.failFast = failFast;
    }

    @Override
    @CpsVmThreadOnly("CPS program calls this, which is run by CpsVmThread")
    public StepExecution start(StepContext context) throws Exception {
        return new ParallelStepExecution(this, context);
    }

    /*package*/ boolean isFailFast() {
        return failFast;
    }


    @PersistIn(PROGRAM)
    static class ResultHandler implements Serializable {
        private final StepContext context;
        private final ParallelStepExecution stepExecution;
        private final boolean failFast;
        /** Have we called stop on the StepExecution? */
        private boolean stopSent = false;

        /**
         * Collect the results of sub-workflows as they complete.
         * The key set is fully populated from the beginning.
         */
        private final Map<String,Outcome> outcomes = new HashMap<String, Outcome>();

        ResultHandler(StepContext context, ParallelStepExecution parallelStepExecution, boolean failFast) {
            this.context = context;
            this.stepExecution = parallelStepExecution;
            this.failFast = failFast;
        }

        Callback callbackFor(String name) {
            outcomes.put(name, null);
            return new Callback(this, name);
        }

        private void stopSent() {
            stopSent = true;
        }

        private boolean isStopSent() {
            return stopSent;
        }

        private static class Callback extends BodyExecutionCallback {

            private final ResultHandler handler;
            private final String name;

            Callback(ResultHandler handler, String name) {
                this.handler = handler;
                this.name = name;
            }

            @Override
            public void onSuccess(StepContext context, Object result) {
                handler.outcomes.put(name, new Outcome(result, null));
                checkAllDone();
            }

            @Override
            public void onFailure(StepContext context, Throwable t) {
                handler.outcomes.put(name, new Outcome(null, t));
                checkAllDone();
            }

            private void checkAllDone() {
                Map<String,Object> success = new HashMap<String, Object>();
                Entry<String,Outcome> failure = null;
                for (Entry<String,Outcome> e : handler.outcomes.entrySet()) {
                    Outcome o = e.getValue();

                    if (o==null) {
                        // some of the results are not yet ready
                        if (handler.failFast && ! handler.isStopSent()) {
                            handler.stopSent();
                            try {
                                handler.stepExecution.stop(new InterruptedException("Interupted due to early termination of parallel step."));
                            }
                            catch (Exception ignored) {
                                // ignored.
                            }
                        }
                        return;
                    }
                    if (o.isFailure()) {
                        failure= e;
                    } else {
                        success.put(e.getKey(), o.getNormal());
                    }
                }

                // all done
                if (failure!=null) {
                    // wrap the exception so that the call stack leading up to parallel is visible
                    handler.context.onFailure(new ParallelStepException(failure.getKey(), failure.getValue().getAbnormal()));
                } else {
                    handler.context.onSuccess(success);
                }
            }

            private static final long serialVersionUID = 1L;
        }

        private static final long serialVersionUID = 1L;
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "parallel";
        }

        @Override
        public Step newInstance(Map<String,Object> arguments) {
            boolean earlyTermination = false;
            Map<String,Closure<?>> closures = new LinkedHashMap<String, Closure<?>>();
            for (Entry<String,Object> e : arguments.entrySet()) {
                if ((e.getValue() instanceof Closure)) {
                    closures.put(e.getKey(), (Closure<?>)e.getValue());
                }
                else if ("earlyTermination".equals(e.getKey())) {
                    earlyTermination = Boolean.valueOf(e.getValue().toString());
                }
                else {
                    throw new IllegalArgumentException("Expected a closure but found "+e.getKey()+"="+e.getValue());
                }
            }
            return new ParallelStep((Map)closures, earlyTermination);
        }

        @Override public Map<String,Object> defineArguments(Step step) throws UnsupportedOperationException {
            ParallelStep ps = (ParallelStep) step;
            Map<String,Object> retVal = new TreeMap<String,Object>(ps.closures);
            if (ps.failFast) {
                retVal.put("earlyTermination", Boolean.TRUE);
            }
            return retVal;
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return Collections.<Class<?>>singleton(TaskListener.class);
        }

        /**
         * Block arguments would have to be wrapped into a list and passed as such.
         * It doesn't make sense to do the following as it is single-thread:
         *
         * <pre>
         * parallel {
         *      foo();
         * }
         * </pre>
         */
        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }

        @Override
        public String getDisplayName() {
            return "Execute sub-workflows in parallel";
        }
    }
}
