package org.jenkinsci.plugins.workflow.steps;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.structs.DescribableHelper;
import org.kohsuke.stapler.ClassDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractStepDescriptorImpl extends StepDescriptor {
    private volatile transient Set<Class<?>> contextTypes;

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

    /** An argument key for a single default parameter. */
    public static final String KEY_VALUE = "value";

    /**
     * Instantiate a new object via {@link DataBoundConstructor} and {@link DataBoundSetter}.
     * If the constructor takes one parameter and the arguments have just {@link #KEY_VALUE} then it is bound to that parameter.
     */
    @Override
    public final Step newInstance(JSONObject arguments) throws Exception {
        if (arguments.keySet().equals(Collections.singleton(KEY_VALUE))) {
            String[] names = new ClassDescriptor(clazz).loadConstructorParamNames();
            if (names.length == 1) {
                arguments = new JSONObject().element(names[0], arguments.get(KEY_VALUE));
            }
        }
        return DescribableHelper.instantiate(clazz, arguments);
    }


    @Override public JSONObject defineArguments(Step step) {
        JSONObject arguments = DescribableHelper.uninstantiate(step);
        String[] names = new ClassDescriptor(step.getClass()).loadConstructorParamNames();
        if (names.length == 1 && arguments.keySet().equals(Collections.singleton(names[0]))) {
            arguments = new JSONObject().element(KEY_VALUE, arguments.get(names[0]));
        }
        return arguments;
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
