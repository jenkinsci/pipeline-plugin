package org.jenkinsci.plugins.workflow.steps;

import java.beans.Introspector;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.inject.Inject;
import org.codehaus.groovy.reflection.ReflectionCache;
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

    /** An argument key for a single default parameter. */
    public static final String KEY_VALUE = "value";

    /**
     * Instantiate a new object via {@link DataBoundConstructor} and {@link DataBoundSetter}.
     * If the constructor takes one parameter and the arguments have just {@link #KEY_VALUE} then it is bound to that parameter.
     */
    @Override
    public final Step newInstance(Map<String,Object> arguments) throws Exception {
        if (arguments.keySet().equals(Collections.singleton(KEY_VALUE))) {
            String[] names = new ClassDescriptor(clazz).loadConstructorParamNames();
            if (names.length == 1) {
                arguments = Collections.singletonMap(names[0], arguments.get(KEY_VALUE));
            }
        }
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
                    Class<?>[] parameterTypes = m.getParameterTypes();
                    if (!m.getName().startsWith("set") || parameterTypes.length != 1) {
                        throw new IllegalStateException(m + " cannot be a @DataBoundSetter");
                    }
                    m.setAccessible(true);
                        Object[] args = buildArguments(arguments, parameterTypes, new String[] {Introspector.decapitalize(m.getName().substring(3))}, false);
                        if (args!=null)
                            m.invoke(o, args);
                }
            }
        }
    }

    private static Object[] buildArguments(Map<String, Object> arguments, Class<?>[] types, String[] names, boolean callEvenIfNoArgs) {
        Object[] args = new Object[names.length];
        boolean hasArg = callEvenIfNoArgs;
        for (int i = 0; i < args.length; i++) {
            // this coercion handles comes from Groovy's ParameterTypes.coerceArgumentsToClasses
            hasArg |= arguments.containsKey(names[i]);
            Object a = arguments.get(names[i]);
            if (a != null) {
                // TODO: this is Groovy specific and should be removed from here
                // but some kind of type coercion would be useful to fix mismatch between Long vs Integer, etc.
                args[i] = ReflectionCache.getCachedClass(types[i]).coerceArgument(a);
            } else if (types[i] == boolean.class) {
                args[i] = false;
            } else if (types[i].isPrimitive() && callEvenIfNoArgs) {
                throw new UnsupportedOperationException("not yet handling @DataBoundConstructor default value of " + types[i] + "; pass an explicit value for " + names[i]);
            }
        }
        return hasArg ? args : null;
    }

    @Override public Map<String,Object> defineArguments(Step step) {
        Map<String,Object> arguments = uninstantiate(step);
        String[] names = new ClassDescriptor(step.getClass()).loadConstructorParamNames();
        if (names.length == 1 && arguments.keySet().equals(Collections.singleton(names[0]))) {
            arguments = Collections.singletonMap(KEY_VALUE, arguments.get(names[0]));
        }
        return arguments;
    }

    /**
     * Computes arguments suitable to pass to {@link #instantiate} to reconstruct this object.
     * @param o a data-bound object
     * @return constructor and/or setter parameters
     * @throws UnsupportedOperationException if the class does not follow the expected structure
     */
    public static Map<String,Object> uninstantiate(Object o) throws UnsupportedOperationException {
        Class<?> clazz = o.getClass();
        Map<String,Object> r = new TreeMap<String,Object>();
        ClassDescriptor d = new ClassDescriptor(clazz);
        for (String name : d.loadConstructorParamNames()) {
            inspect(r, o, clazz, name);
        }
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(DataBoundSetter.class)) {
                    inspect(r, o, clazz, f.getName());
                }
            }
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(DataBoundSetter.class) && m.getName().startsWith("set")) {
                    inspect(r, o, clazz, Introspector.decapitalize(m.getName().substring(3)));
                }
            }
        }
        r.values().removeAll(Collections.singleton(null));
        return r;
    }
    private static void inspect(Map<String,Object> r, Object o, Class<?> clazz, String field) {
        try {
            try {
                r.put(field, clazz.getField(field).get(o));
                return;
            } catch (NoSuchFieldException x) {
                // OK, check for getter instead
            }
            try {
                r.put(field, clazz.getMethod("get" + Character.toUpperCase(field.charAt(0)) + field.substring(1)).invoke(o));
                return;
            } catch (NoSuchMethodException x) {
                // one more check
            }
            try {
                r.put(field, clazz.getMethod("is" + Character.toUpperCase(field.charAt(0)) + field.substring(1)).invoke(o));
            } catch (NoSuchMethodException x) {
                throw new UnsupportedOperationException("no public field ‘" + field + "’ (or getter method) found in " + clazz);
            }
        } catch (UnsupportedOperationException x) {
            throw x;
        } catch (Exception x) {
            throw new UnsupportedOperationException(x);
        }
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
