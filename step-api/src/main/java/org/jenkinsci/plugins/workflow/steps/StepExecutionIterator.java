package org.jenkinsci.plugins.workflow.steps;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.List;

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

    public static ListenableFuture<?> applyAll(Function<StepExecution,Void> f) {
        List<ListenableFuture<?>> futures = new ArrayList<ListenableFuture<?>>();
        for (StepExecutionIterator i : all())
            futures.add(i.apply(f));
        return Futures.allAsList(futures);
    }

    /**
     * Applies only to the specific subtypes.
     */
    public static final <T extends StepExecution> ListenableFuture<?> applyAll(final Class<T> type, final Function<T,Void> f) {
        return applyAll(new Function<StepExecution, Void>() {
            @Override
            public Void apply(StepExecution e) {
                if (type.isInstance(e))
                    f.apply(type.cast(e));
                return null;
            }
        });
    }
}
