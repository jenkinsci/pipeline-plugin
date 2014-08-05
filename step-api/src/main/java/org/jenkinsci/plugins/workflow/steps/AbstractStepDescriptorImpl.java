package org.jenkinsci.plugins.workflow.steps;

import org.codehaus.groovy.reflection.ReflectionCache;
import org.kohsuke.stapler.ClassDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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

    protected final Class<? extends StepExecution> executionType;

    protected AbstractStepDescriptorImpl(Class<? extends StepExecution> executionType) {
        this.executionType = executionType;
    }

    /**
     * Infer {@link #executionType} by the naming convention from {@link #clazz} by appending "Execution" in the end
     */
    protected AbstractStepDescriptorImpl() {
        String name = clazz.getName() + "Execution";
        try {
            this.executionType = clazz.getClassLoader().loadClass(name).asSubclass(StepExecution.class);
        } catch (ClassNotFoundException e) {
            throw (Error)new NoClassDefFoundError("Expected to find "+name).initCause(e);
        }
    }

    public final Class<? extends StepExecution> getExecutionType() {
        return executionType;
    }

    // copied from RequestImpl
    private static <T> Constructor<T> findConstructor(Class<? extends T> clazz, int length) {
        @SuppressWarnings("unchecked") // see Javadoc of getConstructors for this silliness
        Constructor<T>[] ctrs = (Constructor<T>[]) clazz.getConstructors();
        // one with DataBoundConstructor is the most reliable
        for (Constructor<T> c : ctrs) {
            if(c.getAnnotation(DataBoundConstructor.class)!=null) {
                if(c.getParameterTypes().length!=length)
                    throw new IllegalArgumentException(c+" has @DataBoundConstructor but it doesn't match with your .stapler file. Try clean rebuild");
                return c;
            }
        }
        // if not, maybe this was from @stapler-constructor,
        // so look for the constructor with the expected argument length.
        // this is not very reliable.
        for (Constructor<T> c : ctrs) {
            if(c.getParameterTypes().length==length)
                return c;
        }
        throw new IllegalArgumentException(clazz+" does not have a constructor with "+length+" arguments");
    }

    /**
     * Instantiate a new object via DataBoundConstructor and DataBoundSetter.
     */
    @Override
    public final Step newInstance(final Map<String, Object> arguments) throws Exception {
        return instantiate(clazz, arguments);
    }

    /**
     * Creates an instance of a class via {@link DataBoundConstructor}.
     */
    public static <T> T instantiate(Class<? extends T> clazz, Map<String, Object> arguments) throws Exception {
        ClassDescriptor d = new ClassDescriptor(clazz);

        String[] names = d.loadConstructorParamNames();
        Constructor<T> c = findConstructor(clazz, names.length);
        Object[] args = buildArguments(arguments, c.getParameterTypes(), names, true);

        T o  = c.newInstance(args);
        injectSetters(o, arguments);
        return o;
    }

    /**
     * Injects via {@link DataBoundSetter}
     */
    private static void injectSetters(Object o, Map<String, Object> arguments) throws Exception {
        for (Class<?> c = o.getClass(); c!=null; c=c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(DataBoundSetter.class)) {
                    f.setAccessible(true);
                        if (arguments.containsKey(f.getName())) {
                            Object v = arguments.get(f.getName());
                            f.set(o, v);
                        }
                }
            }
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(DataBoundSetter.class)) {
                    String[] names = ClassDescriptor.loadParameterNames(m);

                    m.setAccessible(true);
                        Object[] args = buildArguments(arguments, m.getParameterTypes(), names, false);
                        if (args!=null)
                            m.invoke(o, args);
                }
            }
        }
    }

    // TODO: this is Groovy specific and should be removed from here
    // but some kind of type coercion would be useful to fix mismatch between Long vs Integer, etc.
    private static Object[] buildArguments(Map<String, Object> arguments, Class<?>[] types, String[] names, boolean callEvenIfNoArgs) {
        Object[] args = new Object[names.length];
        boolean hasArg = callEvenIfNoArgs;
        for (int i = 0; i < args.length; i++) {
            // this coercion handles comes from Groovy's ParameterTypes.coerceArgumentsToClasses
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
