package org.jenkinsci.plugins.workflow.cps.steps;

import com.cloudbees.groovy.cps.Outcome;
import groovy.lang.Closure;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
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
    /**
     * All the sub-workflows as {@link Closure}s, keyed by their names.
     */
    /*package*/ final Map<String,Closure> closures;

    public ParallelStep(Map<String,Closure> closures) {
        this.closures = closures;
    }

    @Override
    @CpsVmThreadOnly("CPS program calls this, which is run by CpsVmThread")
    public StepExecution start(StepContext context) throws Exception {
        return new ParallelStepExecution(this, context);
    }

    @PersistIn(FLOW_NODE)
    public static class ParallelLabelAction extends LabelAction {
        private final String branchName;

        ParallelLabelAction(String branchName) {
            super(null);
            this.branchName = branchName;
        }

        @Override
        public String getDisplayName() {
            return "Parallel branch: "+branchName;
        }

        public String getBranchName() {
            return branchName;
        }
    }

    @PersistIn(PROGRAM)
    static class ResultHandler implements Serializable {
        private final StepContext context;
        /**
         * Collect the results of sub-workflows as they complete.
         * The key set is fully populated from the beginning.
         */
        private final Map<String,Outcome> outcomes = new HashMap<String, Outcome>();

        ResultHandler(StepContext context) {
            this.context = context;
        }

        Callback callbackFor(String name) {
            outcomes.put(name, null);
            return new Callback(this, name);
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

                    if (o==null)
                        return; // some of the results are not yet ready
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
            for (Entry<String,Object> e : arguments.entrySet()) {
                if (!(e.getValue() instanceof Closure))
                    throw new IllegalArgumentException("Expected a closure but found "+e.getKey()+"="+e.getValue());
            }
            return new ParallelStep((Map)arguments);
        }

        @Override public Map<String,Object> defineArguments(Step step) throws UnsupportedOperationException {
            return new TreeMap<String,Object>(((ParallelStep) step).closures);
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
