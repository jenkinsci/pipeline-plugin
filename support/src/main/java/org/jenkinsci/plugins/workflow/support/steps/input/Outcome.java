package org.jenkinsci.plugins.workflow.support.steps.input;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;

/**
 * Result of an evaluation.
 *
 * Either represents a value in case of a normal return, or a throwable object in case of abnormal return.
 * Note that both fields can be null, in which case it means a normal return of the value 'null'.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Outcome implements Serializable {
    private final Object normal;
    private final Throwable abnormal;

    public Outcome(Object normal, Throwable abnormal) {
        assert normal==null || abnormal==null;
        this.normal = normal;
        this.abnormal = abnormal;
    }

    /**
     * Like {@link #replay()} but wraps the throwable into {@link InvocationTargetException}.
     */
    public Object wrapReplay() throws InvocationTargetException {
        if (abnormal!=null)
            throw new InvocationTargetException(abnormal);
        else
            return normal;
    }

    public Object replay() throws Throwable {
        if (abnormal!=null)
            throw abnormal;
        else
            return normal;
    }

    public Object getNormal() {
        return normal;
    }

    public Throwable getAbnormal() {
        return abnormal;
    }

    public boolean isSuccess() {
        return abnormal==null;
    }

    public boolean isFailure() {
        return abnormal!=null;
    }

    @Override
    public String toString() {
        if (abnormal!=null)     return "abnormal["+abnormal+']';
        else                    return "normal["+normal+']';
    }

    private static final long serialVersionUID = 1L;
}
