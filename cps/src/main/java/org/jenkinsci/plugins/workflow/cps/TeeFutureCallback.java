package org.jenkinsci.plugins.workflow.cps;

import com.google.common.util.concurrent.FutureCallback;

import java.io.Serializable;

/**
 * @author Kohsuke Kawaguchi
 */
class TeeFutureCallback implements FutureCallback, Serializable {
    private final FutureCallback lhs,rhs;

    public TeeFutureCallback(FutureCallback lhs, FutureCallback rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public void onSuccess(Object result) {
        lhs.onSuccess(result);
        rhs.onSuccess(result);
    }

    @Override
    public void onFailure(Throwable t) {
        lhs.onFailure(t);
        rhs.onFailure(t);
    }

    private static final long serialVersionUID = 1L;
}
