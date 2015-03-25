package org.jenkinsci.plugins.workflow.cps;

import com.google.common.util.concurrent.ListenableFuture;
import hudson.model.Result;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.support.DefaultStepContext;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * {@link StepContext} passed to {@link BodyExecutionCallback}.
 *
 * @author Kohsuke Kawaguchi
 */
final class CpsBodySubContext extends DefaultStepContext {
    /**
     * {@link CpsStepContext} that we delegate most methods to.
     *
     * This context corresponds to the step that invoked the body.
     */
    private final CpsStepContext base;

    /**
     * Node that this sub-context points to.
     */
    private transient FlowNode node;

    CpsBodySubContext(CpsStepContext base, FlowNode node) {
        this.base = base;
        this.node = node;
    }

    @Nonnull
    @Override
    protected FlowNode getNode() throws IOException {
        return node;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CpsBodySubContext that = (CpsBodySubContext) o;
        return base.equals(that.base) && node.equals(that.node);

    }

    @Override
    public int hashCode() {
        return 31 * base.hashCode() + node.hashCode();
    }

    // Delegation to 'base' from here
//======================================


    @Override
    public void onFailure(Throwable t) {
        base.onFailure(t);
    }

    @Override
    public void onSuccess(Object returnValue) {
        base.onSuccess(returnValue);
    }

    @Override
    public void setResult(Result r) {
        base.setResult(r);
    }

    @Override
    public boolean isReady() {
        return base.isReady();
    }

    @Override
    public CpsBodyInvoker newBodyInvoker() {
        return base.newBodyInvoker();
    }

    @Override
    public <T> T doGet(Class<T> key) throws IOException, InterruptedException {
        return base.doGet(key);
    }

    @Override
    public ListenableFuture<Void> saveState() {
        return base.saveState();
    }

    @Nonnull
    @Override
    public CpsFlowExecution getExecution() throws IOException {
        return base.getExecution();
    }

// Delegation to 'base' until here
//======================================

    private static final long serialVersionUID = 1L;
}
