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

import hudson.Extension;
import com.google.common.primitives.Primitives;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import java.beans.Introspector;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import net.java.sezpoz.Index;
import net.java.sezpoz.IndexItem;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ObjectUtils;
import org.codehaus.groovy.reflection.ReflectionCache;
import org.kohsuke.stapler.ClassDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.NoStaplerConstructorException;
import org.kohsuke.stapler.lang.Klass;

/**
 * Utility for converting between {@link Describable}s (and some other objects) and map-like representations.
 * Ultimately should live in Jenkins core (or Stapler).
 */
public class DescribableHelper {

    private static final Logger LOG = Logger.getLogger(DescribableHelper.class.getName());

    /**
     * Creates an instance of a class via {@link DataBoundConstructor} and {@link DataBoundSetter}.
     * <p>The arguments may be primitives (as wrappers) or {@link String}s if that is their declared type.
     * {@link Character}s, {@link Enum}s, and {@link URL}s may be represented by {@link String}s.
     * Other object types may be passed in “raw” as well, but JSON-like structures are encouraged instead.
     * Specifically a {@link List} may be used to represent any list- or array-valued argument.
     * A {@link Map} with {@link String} keys may be used to represent any class which is itself data-bound.
     * In that case the special key {@link #CLAZZ} is used to specify the {@link Class#getName};
     * or it may be omitted if the argument is declared to take a concrete type;
     * or {@link Class#getSimpleName} may be used in case the argument type is {@link Describable}
     * and only one subtype is registered (as a {@link Descriptor}) with that simple name.
     */
    public static <T> T instantiate(Class<? extends T> clazz, Map<String,?> arguments) throws Exception {
        String[] names = loadConstructorParamNames(clazz);
        Constructor<T> c = findConstructor(clazz, names.length);
        Object[] args = buildArguments(clazz, arguments, c.getGenericParameterTypes(), names, true);
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
        String[] names;
        try {
            names = loadConstructorParamNames(clazz);
        } catch (NoStaplerConstructorException x) {
            throw new UnsupportedOperationException(x);
        }
        for (String name : names) {
            inspect(r, o, clazz, name);
        }
        r.values().removeAll(Collections.singleton(null));
        Map<String,Object> constructorOnlyDataBoundProps = new TreeMap<String,Object>(r);
        List<String> dataBoundSetters = new ArrayList<String>();
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(DataBoundSetter.class)) {
                    String field = f.getName();
                    dataBoundSetters.add(field);
                    inspect(r, o, clazz, field);
                }
            }
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(DataBoundSetter.class) && m.getName().startsWith("set")) {
                    String field = Introspector.decapitalize(m.getName().substring(3));
                    dataBoundSetters.add(field);
                    inspect(r, o, clazz, field);
                }
            }
        }
        clearDefaultSetters(clazz, r, constructorOnlyDataBoundProps, dataBoundSetters);
        return r;
    }

    /**
     * Loads a definition of the structure of a class: what kind of data you might get back from {@link #uninstantiate} on an instance,
     * or might want to pass to {@link #instantiate}.
     */
    public static Schema schemaFor(Class<?> clazz) {
        return new Schema(clazz);
    }
    
    private static Schema schemaFor(Class<?> clazz, Stack<String> tracker) {
        return new Schema(clazz, tracker);
    }

    /**
     * Definition of how a particular class may be configured.
     */
    public static final class Schema {

        private final Class<?> type;
        private final Map<String,ParameterType> parameters;
        private final List<String> mandatoryParameters;

        Schema(Class<?> clazz) {
            this(clazz, new Stack<String>());
        }

        Schema(Class<?> clazz, @Nonnull Stack<String> tracker) {
            this.type = clazz;
            /*if(tracker == null){
                tracker = new Stack<String>();
            }*/
            mandatoryParameters = new ArrayList<String>();
            parameters = new TreeMap<String,ParameterType>();
            String[] names = loadConstructorParamNames(clazz);
            Type[] types = findConstructor(clazz, names.length).getGenericParameterTypes();
            for (int i = 0; i < names.length; i++) {
                mandatoryParameters.add(names[i]);
                parameters.put(names[i], ParameterType.of(types[i], tracker));
            }
            for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.isAnnotationPresent(DataBoundSetter.class)) {
                        f.setAccessible(true);
                        parameters.put(f.getName(), ParameterType.of(f.getGenericType(), tracker));
                    }
                }
                for (Method m : c.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(DataBoundSetter.class)) {
                        Type[] parameterTypes = m.getGenericParameterTypes();
                        if (!m.getName().startsWith("set") || parameterTypes.length != 1) {
                            throw new IllegalStateException(m + " cannot be a @DataBoundSetter");
                        }
                        m.setAccessible(true);
                        parameters.put(Introspector.decapitalize(m.getName().substring(3)), ParameterType.of(m.getGenericParameterTypes()[0], tracker));
                    }
                }
            }
        }

        /**
         * A concrete class, usually {@link Describable}.
         */
        public Class<?> getType() {
            return type;
        }

        /**
         * A map from parameter names to types.
         * A parameter name is either the name of an argument to a {@link DataBoundConstructor},
         * or the JavaBeans property name corresponding to a {@link DataBoundSetter}.
         */
        public Map<String,ParameterType> parameters() {
            return parameters;
        }

        /**
         * Mandatory (constructor) parameters, in order.
         * Parameters at the end of the list may be omitted, in which case they are assumed to be null or some other default value
         * (in these cases it would be better to use {@link DataBoundSetter} on the type definition).
         * Will be keys in {@link #parameters}.
         */
        public List<String> mandatoryParameters() {
            return mandatoryParameters;
        }

        /**
         * Corresponds to {@link Descriptor#getDisplayName} where available.
         */
        public String getDisplayName() {
            for (Descriptor<?> d : getDescriptorList()) {
                if (d.clazz == type) {
                    return d.getDisplayName();
                }
            }
            return type.getSimpleName();
        }

        /**
         * Loads help defined for this object as a whole or one of its parameters.
         * Note that you may need to use {@link Util#replaceMacro(String, Map)}
         * to replace {@code ${rootURL}} with some other value.
         * @param parameter if specified, one of {@link #parameters}; else for the whole object
         * @return some HTML (in English locale), if available, else null
         * @see Descriptor#doHelp
         */
        public @CheckForNull String getHelp(@CheckForNull String parameter) throws IOException {
            for (Klass<?> c = Klass.java(type); c != null; c = c.getSuperClass()) {
                URL u = c.getResource(parameter == null ? "help.html" : "help-" + parameter + ".html");
                if (u != null) {
                    return IOUtils.toString(u, "UTF-8");
                }
            }
            return null;
        }

        @Override public String toString() {
            StringBuilder b = new StringBuilder("(");
            boolean first = true;
            Map<String,ParameterType> params = new TreeMap<String,ParameterType>(parameters());
            for (String param : mandatoryParameters()) {
                if (first) {
                    first = false;
                } else {
                    b.append(", ");
                }
                b.append(param).append(": ").append(params.remove(param));
            }
            for (Map.Entry<String,ParameterType> entry : params.entrySet()) {
                if (first) {
                    first = false;
                } else {
                    b.append(", ");
                }
                b.append('[').append(entry.getKey()).append(": ").append(entry.getValue()).append(']');
            }
            return b.append(')').toString();
        }

    }

    /**
     * A type of a parameter to a class.
     */
    public static abstract class ParameterType {
        @Nonnull
        private final Type actualType;

        public Type getActualType() {
            return actualType;
        }

        ParameterType(Type actualType) {
            this.actualType = actualType;
        }
        
        static ParameterType of(Type type){
            return of(type, new Stack<String>());
        }

        private static ParameterType of(Type type, @Nonnull Stack<String> tracker) {
            try {
                if (type instanceof Class) {
                    Class<?> c = (Class<?>) type;
                    if (c == String.class || Primitives.unwrap(c).isPrimitive()) {
                        return new AtomicType(c);
                    }
                    if (Enum.class.isAssignableFrom(c)) {
                        List<String> constants = new ArrayList<String>();
                        for (Enum<?> value : c.asSubclass(Enum.class).getEnumConstants()) {
                            constants.add(value.name());
                        }
                        return new EnumType(c, constants.toArray(new String[constants.size()]));
                    }
                    if (c == URL.class) {
                        return new AtomicType(String.class);
                    }
                    if (c.isArray()) {
                        return new ArrayType(c);
                    }
                    // Assume it is a nested object of some sort.
                    Set<Class<?>> subtypes = findSubtypes(c);
                    if ((subtypes.isEmpty() && !Modifier.isAbstract(c.getModifiers())) || subtypes.equals(Collections.singleton(c))) {
                        // Probably homogeneous. (Might be concrete but subclassable.)
                        return new HomogeneousObjectType(c);
                    } else {
                        // Definitely heterogeneous.
                        Map<String,List<Class<?>>> subtypesBySimpleName = new HashMap<String,List<Class<?>>>();
                        for (Class<?> subtype : subtypes) {
                            String simpleName = subtype.getSimpleName();
                            List<Class<?>> bySimpleName = subtypesBySimpleName.get(simpleName);
                            if (bySimpleName == null) {
                                subtypesBySimpleName.put(simpleName, bySimpleName = new ArrayList<Class<?>>());
                            }
                            bySimpleName.add(subtype);
                        }
                        Map<String,Schema> types = new TreeMap<String,Schema>();
                        for (Map.Entry<String,List<Class<?>>> entry : subtypesBySimpleName.entrySet()) {
                            if (entry.getValue().size() == 1) { // normal case: unambiguous via simple name
                                try {
                                    String key = entry.getKey();
                                    if(tracker.search(key) < 0) {
                                        tracker.push(key);
                                        types.put(key, schemaFor(entry.getValue().get(0), tracker));
                                        tracker.pop();
                                    }
                                } catch (Exception x) {
                                    LOG.log(Level.FINE, "skipping subtype", x);
                                }
                            } else { // have to diambiguate via FQN
                                for (Class<?> subtype : entry.getValue()) {
                                    try {
                                        String name = subtype.getName();
                                        if(tracker.search(name) < 0) {
                                            tracker.push(name);
                                            types.put(name, schemaFor(subtype, tracker));
                                            tracker.pop();
                                        }
                                    } catch (Exception x) {
                                        LOG.log(Level.FINE, "skipping subtype", x);
                                    }
                                }
                            }
                        }
                        return new HeterogeneousObjectType(c, types);
                    }
                }
                if (acceptsList(type)) {
                    return new ArrayType(type, of(((ParameterizedType) type).getActualTypeArguments()[0]));
                }
                throw new UnsupportedOperationException("do not know how to categorize attributes of type " + type);
            } catch (Exception x) {
                return new ErrorType(x, type);
            }
        }
    }

    public static final class AtomicType extends ParameterType {
        AtomicType(Class<?> clazz) {
            super(clazz);
        }

        public Class<?> getType() {
            return (Class) getActualType();
        }

        @Override public String toString() {
            return Primitives.unwrap((Class)getActualType()).getSimpleName();
        }
    }

    public static final class EnumType extends ParameterType {
        private final String[] values;
        EnumType(Class<?> clazz, String[] values) {
            super(clazz);
            this.values = values;
        }

        public Class<?> getType() {
            return (Class) getActualType();
        }

        /**
         * A list of enumeration values.
         */
        public String[] getValues() {
            return values.clone();
        }
        @Override public String toString() {
            return ((Class)getActualType()).getSimpleName() + Arrays.toString(values);
        }
    }

    public static final class ArrayType extends ParameterType {
        private final ParameterType elementType;
        ArrayType(Class<?> actualClass) {
            this(actualClass, of(actualClass.getComponentType()));
        }

        ArrayType(Type actualClass, ParameterType elementType) {
            super(actualClass);
            this.elementType = elementType;
        }

        /**
         * The element type of the array or list.
         */
        public ParameterType getElementType() {
            return elementType;
        }
        @Override public String toString() {
            return elementType + "[]";
        }
    }

    public static final class HomogeneousObjectType extends ParameterType {
        private final Schema type;
        HomogeneousObjectType(Class<?> actualClass) {
            super(actualClass);
            this.type = schemaFor(actualClass);
        }

        public Class<?> getType() {
            return (Class) getActualType();
        }

        /**
         * The schema representing a type of nested object.
         */
        public Schema getSchemaType() {
            return type;
        }

        /**
         * The actual class underlying the type.
         */
        @Override public String toString() {
            return type.getType().getSimpleName() + type;
        }
    }

    /**
     * A parameter (or array element) which could take any of the indicated concrete object types.
     */
    public static final class HeterogeneousObjectType extends ParameterType {
        private final Map<String,Schema> types;
        HeterogeneousObjectType(Class<?> supertype, Map<String,Schema> types) {
            super(supertype);
            this.types = types;
        }

        public Class<?> getType() {
            return (Class) getActualType();
        }

        /**
         * A map from names which could be passed to {@link #CLAZZ} to types of allowable nested objects.
         */
        public Map<String,Schema> getTypes() {
            return types;
        }
        @Override public String toString() {
            return getType().getSimpleName() + types;
        }
    }

    public static final class ErrorType extends ParameterType {
        private final Exception error;
        ErrorType(Exception error, Type type) {
            super(type);
            LOG.log(Level.FINE, null, error);
            this.error = error;
        }
        public Exception getError() {
            return error;
        }
        @Override public String toString() {
            return error.toString();
        }
    }

    /**
     * Removes configuration of any properties based on {@link DataBoundSetter} which appear unmodified from the default.
     * @param clazz the class of {@code o}
     * @param allDataBoundProps all its properties, including those from its {@link DataBoundConstructor} as well as any {@link DataBoundSetter}s; some of the latter might be deleted
     * @param constructorOnlyDataBoundProps properties from {@link DataBoundConstructor} only
     * @param dataBoundSetters a list of property names marked with {@link DataBoundSetter}
     */
    private static void clearDefaultSetters(Class<?> clazz, Map<String,Object> allDataBoundProps, Map<String,Object> constructorOnlyDataBoundProps, Collection<String> dataBoundSetters) {
        if (dataBoundSetters.isEmpty()) {
            return;
        }
        Object control;
        try {
            control = instantiate(clazz, constructorOnlyDataBoundProps);
        } catch (Exception x) {
            LOG.log(Level.WARNING, "Cannot create control version of " + clazz + " using " + constructorOnlyDataBoundProps, x);
            return;
        }
        Map<String,Object> fromControl = new HashMap<String,Object>(constructorOnlyDataBoundProps);
        Iterator<String> fields = dataBoundSetters.iterator();
        while (fields.hasNext()) {
            String field = fields.next();
            try {
                inspect(fromControl, control, clazz, field);
            } catch (RuntimeException x) {
                LOG.log(Level.WARNING, "Failed to check property " + field + " of " + clazz + " on " + control, x);
                fields.remove();
            }
        }
        for (String field : dataBoundSetters) {
            if (ObjectUtils.equals(allDataBoundProps.get(field), fromControl.get(field))) {
                allDataBoundProps.remove(field);
            }
        }
    }

    public static final String CLAZZ = "$class";

    private static Object[] buildArguments(Class<?> clazz, Map<String,?> arguments, Type[] types, String[] names, boolean callEvenIfNoArgs) throws Exception {
        Object[] args = new Object[names.length];
        boolean hasArg = callEvenIfNoArgs;
        for (int i = 0; i < args.length; i++) {
            String name = names[i];
            hasArg |= arguments.containsKey(name);
            Object a = arguments.get(name);
            Type type = types[i];
            if (a != null) {
                args[i] = coerce(clazz.getName() + "." + name, type, a);
            } else if (type == boolean.class) {
                args[i] = false;
            } else if (type instanceof Class && ((Class) type).isPrimitive() && callEvenIfNoArgs) {
                throw new UnsupportedOperationException("not yet handling @DataBoundConstructor default value of " + type + "; pass an explicit value for " + name);
            } else {
                // TODO this might be fine (ExecutorStep.label), or not (GenericSCMStep.scm); should inspect parameter annotations for @Nonnull and throw an UOE if found
            }
        }
        return hasArg ? args : null;
    }

    @SuppressWarnings("unchecked")
    private static Object coerce(String context, Type type, @Nonnull Object o) throws Exception {
        if (type instanceof Class) {
            o = ReflectionCache.getCachedClass((Class) type).coerceArgument(o);
        }
        if (type instanceof Class && Primitives.wrap((Class) type).isInstance(o)) {
            return o;
        } else if (o instanceof Map) {
            Map<String,Object> m = new HashMap<String,Object>();
            for (Map.Entry<?,?> entry : ((Map<?,?>) o).entrySet()) {
                m.put((String) entry.getKey(), entry.getValue());
            }

            String clazzS = (String) m.remove(CLAZZ);
            Class<?> clazz;
            if (clazzS == null) {
                if (Modifier.isAbstract(((Class) type).getModifiers())) {
                    throw new UnsupportedOperationException("must specify " + CLAZZ + " with an implementation of " + type);
                }
                clazz = (Class) type;
            } else if (clazzS.contains(".")) {
                Jenkins j = Jenkins.getInstance();
                ClassLoader loader = j != null ? j.getPluginManager().uberClassLoader : DescribableHelper.class.getClassLoader();
                clazz = loader.loadClass(clazzS);
            } else if (type instanceof Class) {
                clazz = null;
                for (Class<?> c : findSubtypes((Class<?>) type)) {
                    if (c.getSimpleName().equals(clazzS)) {
                        if (clazz != null) {
                            throw new UnsupportedOperationException(clazzS + " as a " + type +  " could mean either " + clazz.getName() + " or " + c.getName());
                        }
                        clazz = c;
                    }
                }
                if (clazz == null) {
                    throw new UnsupportedOperationException("no known implementation of " + type + " is named " + clazzS);
                }
            } else {
                throw new UnsupportedOperationException("JENKINS-26535: do not know how to handle " + type);
            }
            return instantiate(clazz.asSubclass((Class<?>) type), m);
        } else if (o instanceof String && type instanceof Class && ((Class) type).isEnum()) {
            return Enum.valueOf(((Class) type).asSubclass(Enum.class), (String) o);
        } else if (o instanceof String && type == URL.class) {
            return new URL((String) o);
        } else if (o instanceof String && (type == char.class || type == Character.class) && ((String) o).length() == 1) {
            return ((String) o).charAt(0);
        } else if (o instanceof List && type instanceof Class && ((Class) type).isArray()) {
            Class<?> componentType = ((Class) type).getComponentType();
            List<Object> list = mapList(context, componentType, (List) o);
            return list.toArray((Object[]) Array.newInstance(componentType, list.size()));
        } else if (o instanceof List && acceptsList(type)) {
            return mapList(context, ((ParameterizedType) type).getActualTypeArguments()[0], (List) o);
        } else {
            throw new ClassCastException(context + " expects " + type + " but received " + o.getClass());
        }
    }

    /** Whether this type is generic of {@link List} or a supertype thereof (such as {@link Collection}). */
    @SuppressWarnings("unchecked")
    private static boolean acceptsList(Type type) {
        return type instanceof ParameterizedType && ((ParameterizedType) type).getRawType() instanceof Class && ((Class) ((ParameterizedType) type).getRawType()).isAssignableFrom(List.class);
    }

    private static List<Object> mapList(String context, Type type, List<?> list) throws Exception {
        List<Object> r = new ArrayList<Object>();
        for (Object elt : list) {
            r.add(coerce(context, type, elt));
        }
        return r;
    }

    private static String[] loadConstructorParamNames(Class<?> clazz) {
        if (clazz == ParametersDefinitionProperty.class) { // TODO pending core fix
            return new String[] {"parameterDefinitions"};
        }
        return new ClassDescriptor(clazz).loadConstructorParamNames();
    }

    // adapted from RequestImpl
    @SuppressWarnings("unchecked")
    private static <T> Constructor<T> findConstructor(Class<? extends T> clazz, int length) {
        try { // may work without this, but only if the JVM happens to return the right overload first
            if (clazz == ParametersDefinitionProperty.class && length == 1) { // TODO pending core fix
                return (Constructor<T>) ParametersDefinitionProperty.class.getConstructor(List.class);
            }
        } catch (NoSuchMethodException x) {
            throw new AssertionError(x);
        }
        Constructor<T>[] ctrs = (Constructor<T>[]) clazz.getConstructors();
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
                        f.set(o, v != null ? coerce(c.getName() + "." + f.getName(), f.getType(), v) : null);
                    }
                }
            }
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(DataBoundSetter.class)) {
                    Type[] parameterTypes = m.getGenericParameterTypes();
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
        AtomicReference<Type> type = new AtomicReference<Type>();
        Object value = inspect(o, clazz, field, type);
        try {
            String[] names = loadConstructorParamNames(clazz);
            int idx = Arrays.asList(names).indexOf(field);
            if (idx >= 0) {
                Type ctorType = findConstructor(clazz, names.length).getGenericParameterTypes()[idx];
                if (!type.get().equals(ctorType)) {
                    LOG.log(Level.WARNING, "For {0}.{1}, preferring constructor type {2} to differing getter type {3}", new Object[] {clazz.getName(), field, ctorType, type});
                    type.set(ctorType);
                }
            }
        } catch (IllegalArgumentException x) {
            // From loadConstructorParamNames or findConstructor; ignore
        }
        r.put(field, uncoerce(value, type.get()));
    }

    private static Object uncoerce(Object o, Type type) {
        if (type instanceof Class && ((Class) type).isEnum() && o instanceof Enum) {
            return ((Enum) o).name();
        } else if (type == URL.class && o instanceof URL) {
            return ((URL) o).toString();
        } else if ((type == Character.class || type == char.class) && o instanceof Character) {
            return ((Character) o).toString();
        } else if (o instanceof Object[]) {
            List<Object> list = new ArrayList<Object>();
            Object[] array = (Object[]) o;
            for (Object elt : array) {
                list.add(uncoerce(elt, array.getClass().getComponentType()));
            }
            return list;
        } else if (o instanceof List && acceptsList(type)) {
            List<Object> list = new ArrayList<Object>();
            for (Object elt : (List<?>) o) {
                list.add(uncoerce(elt, ((ParameterizedType) type).getActualTypeArguments()[0]));
            }
            return list;
        } else if (o != null && !o.getClass().getName().startsWith("java.")) {
            try {
                // Check to see if this can be treated as a data-bound struct.
                Map<String, Object> nested = uninstantiate(o);
                if (type != o.getClass()) {
                    nested.put(CLAZZ, o.getClass().getSimpleName());
                }
                return nested;
            } catch (UnsupportedOperationException x) {
                // then leave it raw
                if (!(x.getCause() instanceof NoStaplerConstructorException)) {
                    LOG.log(Level.WARNING, "failed to uncoerce " + o, x);
                }
            }
        }
        return o;
    }

    private static Object inspect(Object o, Class<?> clazz, String field, AtomicReference<Type> type) {
        try {
            try {
                Field f = clazz.getField(field);
                type.set(f.getGenericType());
                return f.get(o);
            } catch (NoSuchFieldException x) {
                // OK, check for getter instead
            }
            try {
                Method m = clazz.getMethod("get" + Character.toUpperCase(field.charAt(0)) + field.substring(1));
                type.set(m.getGenericReturnType());
                return m.invoke(o);
            } catch (NoSuchMethodException x) {
                // one more check
            }
            try {
                type.set(boolean.class);
                return clazz.getMethod("is" + Character.toUpperCase(field.charAt(0)) + field.substring(1)).invoke(o);
            } catch (NoSuchMethodException x) {
                throw new UnsupportedOperationException("no public field ‘" + field + "’ (or getter method) found in " + clazz);
            }
        } catch (UnsupportedOperationException x) {
            throw x;
        } catch (Exception x) {
            throw new UnsupportedOperationException(x);
        }
    }

    static Set<Class<?>> findSubtypes(Class<?> supertype) {
        Set<Class<?>> clazzes = new HashSet<Class<?>>();
        for (Descriptor<?> d : getDescriptorList()) {
            if (supertype.isAssignableFrom(d.clazz)) {
                clazzes.add(d.clazz);
            }
        }
        if (supertype == ParameterValue.class) { // TODO JENKINS-26093 hack, pending core change
            for (Class<?> d : findSubtypes(ParameterDefinition.class)) {
                String name = d.getName();
                if (name.endsWith("Definition")) {
                    try {
                        Class<?> c = d.getClassLoader().loadClass(name.replaceFirst("Definition$", "Value"));
                        if (supertype.isAssignableFrom(c)) {
                            clazzes.add(c);
                        }
                    } catch (ClassNotFoundException x) {
                        // ignore
                    }
                }
            }
        }
        return clazzes;
    }

    @SuppressWarnings("rawtypes")
    private static List<? extends Descriptor> getDescriptorList() {
        Jenkins j = Jenkins.getInstance();
        if (j != null) {
            // Jenkins.getDescriptorList does not work well since it is limited to descriptors declaring one supertype, and does not work at all for SimpleBuildStep.
            return j.getExtensionList(Descriptor.class);
        } else {
            // TODO should be part of ExtensionList.lookup in core, but here now for benefit of tests:
            List<Descriptor<?>> descriptors = new ArrayList<Descriptor<?>>();
            for (IndexItem<Extension,Object> item : Index.load(Extension.class, Object.class)) {
                try {
                    Object o = item.instance();
                    if (o instanceof Descriptor) {
                        descriptors.add((Descriptor) o);
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
