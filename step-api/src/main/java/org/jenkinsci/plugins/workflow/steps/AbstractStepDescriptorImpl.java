package org.jenkinsci.plugins.workflow.steps;

import org.codehaus.groovy.reflection.ReflectionCache;
import org.kohsuke.stapler.ClassDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
* @author Kohsuke Kawaguchi
*/
public abstract class AbstractStepDescriptorImpl extends StepDescriptor {
    private volatile transient Set<Class<?>> contextTypes;

    /**
     * Switch to ClassDescriptor.findConstructor() post Stapler1.225
     */
    private Constructor findConstructor(int length) {
        Constructor<?>[] ctrs = clazz.getConstructors();
        // one with DataBoundConstructor is the most reliable
        for (Constructor c : ctrs) {
            if(c.getAnnotation(DataBoundConstructor.class)!=null) {
                if(c.getParameterTypes().length!=length)
                    throw new IllegalArgumentException(c+" has @DataBoundConstructor but it doesn't match with your .stapler file. Try clean rebuild");
                return c;
            }
        }
        // if not, maybe this was from @stapler-constructor,
        // so look for the constructor with the expected argument length.
        // this is not very reliable.
        for (Constructor c : ctrs) {
            if(c.getParameterTypes().length==length)
                return c;
        }
        throw new IllegalArgumentException(clazz+" does not have a constructor with "+length+" arguments");
    }

    /**
     * Instantiate a new object via DataBoundConstructor and DataBoundSetter.
     */
    @Override
    public Step newInstance(final Map<String, Object> arguments) {
        Step step = instantiate(arguments);
        injectSetters(step, arguments);
        return step;
    }

    /**
     * Creates an instance of {@link Step} via {@link DataBoundConstructor}
     */
    protected Step instantiate(Map<String, Object> arguments) {
        ClassDescriptor d = new ClassDescriptor(clazz);

        String[] names = d.loadConstructorParamNames();
        Constructor c = findConstructor(names.length);
        Object[] args = buildArguments(arguments, c.getParameterTypes(), names, true);

        try {
            return (Step) c.newInstance(args);
        } catch (InstantiationException e) {
            throw (Error)new InstantiationError(e.getMessage()).initCause(e);
        } catch (IllegalAccessException e) {
            throw (Error)new IllegalAccessError(e.getMessage()).initCause(e);
        } catch (InvocationTargetException e) {
            throw (Error)new InstantiationError(e.getMessage()).initCause(e);
        }
    }

    /**
     * Injects via {@link DataBoundSetter}
     */
    private void injectSetters(Step step, Map<String, Object> arguments) {
        for (Class c = step.getClass(); c!=null; c=c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(DataBoundSetter.class)) {
                    f.setAccessible(true);
                    try {
                        if (arguments.containsKey(f.getName())) {
                            Object v = arguments.get(f.getName());
                            f.set(step, v);
                        }
                    } catch (IllegalAccessException e) {
                        throw (Error)new IllegalAccessError(e.getMessage()).initCause(e);
                    }
                }
            }
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(DataBoundSetter.class)) {
                    String[] names = ClassDescriptor.loadParameterNames(m);

                    m.setAccessible(true);
                    try {
                        Object[] args = buildArguments(arguments, m.getParameterTypes(), names, false);
                        if (args!=null)
                            m.invoke(step, args);
                    } catch (IllegalAccessException e) {
                        throw (Error)new IllegalAccessError(e.getMessage()).initCause(e);
                    } catch (InvocationTargetException e) {
                        throw (Error)new InstantiationError(e.getMessage()).initCause(e);
                    }
                }
            }
        }
    }

    private Object[] buildArguments(Map<String, Object> arguments, Class[] types, String[] names, boolean callEvenIfNoArgs) {
        Object[] args = new Object[names.length];
        boolean hasArg = callEvenIfNoArgs;
        for (int i = 0; i < args.length; i++) {
            // this coercion handles comes from ParameterTypes.coerceArgumentsToClasses
            hasArg |= arguments.containsKey(names[i]);
            Object a = arguments.get(names[i]);
            if (a!=null)
                args[i] = ReflectionCache.getCachedClass(types[i]).coerceArgument(a);
        }
        return hasArg ? args : null;
    }

    /**
     * Looks for the fields and setter methods with {@link StepContextParameter}s
     * and infer required contexts from there.
     */
    @Override
    public Set<Class<?>> getRequiredContext() {
        if (contextTypes==null) {
            Set<Class<?>> r = new HashSet<Class<?>>();

            for (Field f : clazz.getDeclaredFields()) {
                if (f.isAnnotationPresent(StepContextParameter.class)) {
                    r.add(f.getType());
                }
            }
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.isAnnotationPresent(StepContextParameter.class)) {
                    Collections.addAll(r, m.getParameterTypes());
                }
            }

            contextTypes = r;
        }

        return contextTypes;
    }
}
