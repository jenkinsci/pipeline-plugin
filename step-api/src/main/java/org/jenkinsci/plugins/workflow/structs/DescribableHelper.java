/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.structs;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import com.google.common.primitives.Primitives;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.beans.Introspector;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import jenkins.model.Jenkins;
import net.java.sezpoz.Index;
import net.java.sezpoz.IndexItem;
import org.codehaus.groovy.reflection.ReflectionCache;
import org.kohsuke.stapler.ClassDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Utility for converting between {@link Describable}s (and some other objects) and map-like representations.
 * Ultimately should live in Jenkins core (or Stapler).
 */
public class DescribableHelper {

    /**
     * Creates an instance of a class via {@link DataBoundConstructor} and {@link DataBoundSetter}.
     */
    public static <T> T instantiate(Class<? extends T> clazz, Map<String,?> arguments) throws Exception {
        ClassDescriptor d = new ClassDescriptor(clazz);
        String[] names = d.loadConstructorParamNames();
        Constructor<T> c = findConstructor(clazz, names.length);
        Object[] args = buildArguments(clazz, arguments, c.getParameterTypes(), names, true);
        T o = c.newInstance(args);
        injectSetters(o, arguments);
        return o;
    }

    /**
     * Computes arguments suitable to pass to {@link #instantiate} to reconstruct this object.
     * @param o a data-bound object
     * @return constructor and/or setter parameters
     * @throws UnsupportedOperationException if the class does not follow the expected structure
     */
    public static Map<String,Object> uninstantiate(Object o) throws UnsupportedOperationException {
        Class<?> clazz = o.getClass();
        Map<String, Object> r = new TreeMap<String, Object>();
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

    private static Object[] buildArguments(Class<?> clazz, Map<String,?> arguments, Class<?>[] types, String[] names, boolean callEvenIfNoArgs) {
        Object[] args = new Object[names.length];
        boolean hasArg = callEvenIfNoArgs;
        for (int i = 0; i < args.length; i++) {
            String name = names[i];
            hasArg |= arguments.containsKey(name);
            Object a = arguments.get(name);
            Class<?> type = types[i];
            if (a != null) {
                Object coerced = ReflectionCache.getCachedClass(type).coerceArgument(a);
                if (!Primitives.wrap(type).isInstance(coerced)) {
                    throw new ClassCastException(clazz.getName() + "." + name + " expects " + type.getName() + " but received " + coerced.getClass().getName());
                }
                args[i] = coerced;
            } else if (type == boolean.class) {
                args[i] = false;
            } else if (type.isPrimitive() && callEvenIfNoArgs) {
                throw new UnsupportedOperationException("not yet handling @DataBoundConstructor default value of " + type + "; pass an explicit value for " + name);
            }
        }
        return hasArg ? args : null;
    }

    // copied from RequestImpl
    private static <T> Constructor<T> findConstructor(Class<? extends T> clazz, int length) {
        @SuppressWarnings("unchecked") Constructor<T>[] ctrs = (Constructor<T>[]) clazz.getConstructors();
        for (Constructor<T> c : ctrs) {
            if (c.getAnnotation(DataBoundConstructor.class) != null) {
                if (c.getParameterTypes().length != length) {
                    throw new IllegalArgumentException(c + " has @DataBoundConstructor but it doesn't match with your .stapler file. Try clean rebuild");
                }
                return c;
            }
        }
        for (Constructor<T> c : ctrs) {
            if (c.getParameterTypes().length == length) {
                return c;
            }
        }
        throw new IllegalArgumentException(clazz + " does not have a constructor with " + length + " arguments");
    }

    /**
     * Injects via {@link DataBoundSetter}
     */
    private static void injectSetters(Object o, Map<String,?> arguments) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
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
                    Object[] args = buildArguments(c, arguments, parameterTypes, new String[] {Introspector.decapitalize(m.getName().substring(3))}, false);
                    if (args != null) {
                        m.invoke(o, args);
                    }
                }
            }
        }
    }

    private static void inspect(Map<String, Object> r, Object o, Class<?> clazz, String field) {
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

    static <T> Set<Class<? extends T>> findSubtypes(Class<T> supertype) {
        if (Describable.class.isAssignableFrom(supertype)) {
            Set<Class<? extends T>> clazzes = new HashSet<Class<? extends T>>();
            for (Descriptor<?> d : getDescriptorList(supertype/*.asSubclass(Describable.class)*/)) {
                clazzes.add(d.clazz.asSubclass(supertype));
            }
            return clazzes;
        } else {
            return Collections.emptySet();
        }
    }

    private static List<? extends Descriptor<?>> getDescriptorList(Class<? /* extends Describable*/> supertype) {
        Jenkins j = Jenkins.getInstance();
        if (j != null) {
            @SuppressWarnings("unchecked")
            DescriptorExtensionList<?,?> descriptors = j.getDescriptorList(supertype.asSubclass(Describable.class));
            return descriptors;
        } else {
            // TODO should be part of ExtensionList.lookup in core, but here now for benefit of tests:
            List<Descriptor<?>> descriptors = new ArrayList<Descriptor<?>>();
            for (IndexItem<Extension,Object> item : Index.load(Extension.class, Object.class)) {
                try {
                    Object o = item.instance();
                    if (o instanceof Descriptor) {
                        Descriptor<?> d = (Descriptor) o;
                        if (supertype.isAssignableFrom(d.clazz)) {
                            descriptors.add(d);
                        }
                    }
                } catch (InstantiationException x) {
                    // ignore for now
                }
            }
            return descriptors;
        }
    }

    private DescribableHelper() {}

}
