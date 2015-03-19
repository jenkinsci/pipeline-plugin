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
import java.util.AbstractMap.SimpleEntry;
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

    /** should a failure in a parallel branch terminate other still executing branches. */
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
        /** if we failFast we need to record the first failure */
        private SimpleEntry<String,Throwable> originalFailure = null;

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
                checkAllDone(false);
            }

            @Override
            public void onFailure(StepContext context, Throwable t) {
                handler.outcomes.put(name, new Outcome(null, t));
                if (handler.originalFailure == null) {
                    handler.originalFailure = new SimpleEntry<String, Throwable>(name, t);
                }
                checkAllDone(true);
            }

            private void checkAllDone(boolean stepFailed) {
                Map<String,Object> success = new HashMap<String, Object>();
                for (Entry<String,Outcome> e : handler.outcomes.entrySet()) {
                    Outcome o = e.getValue();

                    if (o==null) {
                        // some of the results are not yet ready
                        if (stepFailed && handler.failFast && ! handler.isStopSent()) {
                            handler.stopSent();
                            try {
                                handler.stepExecution.stop(new FailFastException());
                            }
                            catch (Exception ignored) {
                                // ignored.
                            }
                        }
                        return;
                    }
                    if (o.isFailure()) {
                        if (handler.originalFailure == null) {
                            // in case the plugin is upgraded whilst a parallel step is running
                            handler.originalFailure = new SimpleEntry<String, Throwable>(e.getKey(), e.getValue().getAbnormal());
                        }
                        // recorded in the onFailure
                    } else {
                        success.put(e.getKey(), o.getNormal());
                    }
                }
                // all done
                if (handler.originalFailure!=null) {
                    // wrap the exception so that the call stack leading up to parallel is visible
                    handler.context.onFailure(new ParallelStepException(handler.originalFailure.getKey(), handler.originalFailure.getValue()));
                } else {
                    handler.context.onSuccess(success);
                }
            }
            
            private static final long serialVersionUID = 1L;
        }

        private static final long serialVersionUID = 1L;
    }

    /** Internal exception that is only used internally to abort a parallel body in the case of a failFast body failing. */
    private static final class FailFastException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        private final static String FAIL_FAST_FLAG = "failFast";

        @Override
        public String getFunctionName() {
            return "parallel";
        }

        @Override
        public Step newInstance(Map<String,Object> arguments) {
            boolean failFast = false;
            Map<String,Closure<?>> closures = new LinkedHashMap<String, Closure<?>>();
            for (Entry<String,Object> e : arguments.entrySet()) {
                if ((e.getValue() instanceof Closure)) {
                    closures.put(e.getKey(), (Closure<?>)e.getValue());
                }
                else if (FAIL_FAST_FLAG.equals(e.getKey()) && e.getValue() instanceof Boolean) {
                    failFast = (Boolean)e.getValue();
                }
                else {
                    throw new IllegalArgumentException("Expected a closure or failFast but found "+e.getKey()+"="+e.getValue());
                }
            }
            return new ParallelStep((Map)closures, failFast);
        }

        @Override public Map<String,Object> defineArguments(Step step) throws UnsupportedOperationException {
            ParallelStep ps = (ParallelStep) step;
            Map<String,Object> retVal = new TreeMap<String,Object>(ps.closures);
            if (ps.failFast) {
                retVal.put(FAIL_FAST_FLAG, Boolean.TRUE);
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
