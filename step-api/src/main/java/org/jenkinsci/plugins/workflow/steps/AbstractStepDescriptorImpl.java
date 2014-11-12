package org.jenkinsci.plugins.workflow.steps;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractStepDescriptorImpl extends StepDescriptor {
    private volatile transient Set<Class<?>> contextTypes;

    // TODO should this be required to be AbstractStepExecutionImpl?
    private final Class<? extends StepExecution> executionType;

    /**
     * @param executionType an associated execution class; the {@link Step} (usually an {@link AbstractStepImpl}) can be {@link Inject}ed as {@code transient}; {@link StepContextParameter} may be used on {@code transient} fields as well
     */
    protected AbstractStepDescriptorImpl(Class<? extends StepExecution> executionType) {
        this.executionType = executionType;
    }

    public final Class<? extends StepExecution> getExecutionType() {
        return executionType;
    }

    /**
     * Looks for the fields and setter methods with {@link StepContextParameter}s
     * and infer required contexts from there.
     */
    @Override
    public final Set<Class<?>> getRequiredContext() {
        if (contextTypes==null) {
            Set<Class<?>> r = new HashSet<Class<?>>();

            for (Class<?> c = executionType; c!=null; c=c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.isAnnotationPresent(StepContextParameter.class)) {
                        r.add(f.getType());
                    }
                }
                for (Method m : c.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(StepContextParameter.class)) {
                        Collections.addAll(r, m.getParameterTypes());
                    }
                }
            }

            contextTypes = r;
        }

        return contextTypes;
    }
}
