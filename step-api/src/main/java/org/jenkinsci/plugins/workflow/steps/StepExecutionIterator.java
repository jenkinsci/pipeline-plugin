package org.jenkinsci.plugins.workflow.steps;

import com.google.common.base.Function;
import com.google.common.util.concurrent.ListenableFuture;
import hudson.ExtensionList;
import hudson.ExtensionPoint;

/**
 * Enumerates active running {@link StepExecution}s in the system.
 * @see StepExecution#applyAll(Class, Function)
 * @author Kohsuke Kawaguchi
 */
public abstract class StepExecutionIterator implements ExtensionPoint {
    /**
     * Finds all the ongoing {@link StepExecution} and apply the function.
     *
     * The control flow is inverted because a major use case (workflow) loads
     * {@link StepExecution}s asynchronously (for example when workflow run
     * is blocked trying to restore pickles.)
     *
     * @return
     *      {@link ListenableFuture} to signal the completion of the application.
     */
    public abstract ListenableFuture<?> apply(Function<StepExecution,Void> f);

    public static ExtensionList<StepExecutionIterator> all() {
        return ExtensionList.lookup(StepExecutionIterator.class);
    }
}
