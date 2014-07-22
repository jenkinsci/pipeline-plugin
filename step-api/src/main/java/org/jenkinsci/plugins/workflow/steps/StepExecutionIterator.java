package org.jenkinsci.plugins.workflow.steps;

import com.google.common.base.Function;
import com.google.common.util.concurrent.ListenableFuture;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

/**
 * Enumerates active running {@link StepExecution}s in the system.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class StepExecutionIterator implements ExtensionPoint {
    /**
     * Finds all the ongoing {@link StepExecution} and apply the function.
     *
     * The control flow is inverted because a major use case (workflow) loads
     * {@link StepExecution}s asynchronously.
     *
     * @return
     *      {@link ListenableFuture} to signal the completion of the application.
     */
    public abstract ListenableFuture<?> apply(Function<StepExecution,Void> f);

    public static ExtensionList<StepExecutionIterator> all() {
        return Jenkins.getInstance().getExtensionList(StepExecutionIterator.class);
    }
}
