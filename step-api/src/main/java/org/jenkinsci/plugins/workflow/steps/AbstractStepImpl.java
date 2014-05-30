package org.jenkinsci.plugins.workflow.steps;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.MembersInjector;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.ClassDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.inject.Inject;
import java.beans.Introspector;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Partial step implementation that uses annotations to
 * inject context variables and parameters.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractStepImpl extends Step {
    @Override
    public boolean start(StepContext context) throws Exception {
        inject(context);
        return doStart(context);
    }

    /**
     * Subtype will implement this method and put the meat of the {@link #start(StepContext)} processing.
     */
    protected abstract boolean doStart(StepContext context);

    public static abstract class DescriptorImpl extends StepDescriptor {
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
         * Instantiate a new object via
         */
        @Override
        public Step newInstance(final Map<String, Object> arguments) {
            Step step = instantiate(arguments);
            Injector i = prepareInjector(arguments);
            i.injectMembers(step);
            return step;
        }

        /**
         * Creates an instance of {@link Step} via {@link DataBoundConstructor}
         */
        protected Step instantiate(Map<String, Object> arguments) {
            ClassDescriptor d = new ClassDescriptor(clazz);

            String[] names = d.loadConstructorParamNames();
            Constructor c = findConstructor(names.length);
            Object[] args = new Object[names.length];
            for (int i = 0; i < args.length; i++) {
                args[i] = arguments.get(names[i]);
            }

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
         * Performs injection via Guice to {@link Inject}, {@link StepParameter}, {@link StepContextParameter}.
         */
        protected Injector prepareInjector(final Map<String, Object> arguments) {
            return Jenkins.getInstance().getInjector().createChildInjector(new AbstractModule() {
                        @Override
                        protected void configure() {
                            bindListener(Matchers.any(),new TypeListener() {
                                @Override
                                public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
                                    for (Field f : type.getRawType().getDeclaredFields()) {
                                        if (f.isAnnotationPresent(DataBoundSetter.class)) {
                                            encounter.register(new FieldInjector<I>(f));
                                        }
                                    }
                                    for (Method m : type.getRawType().getDeclaredMethods()) {
                                        if (m.isAnnotationPresent(DataBoundSetter.class)) {
                                            encounter.register(new MethodInjector<I>(m));
                                        }
                                    }
                                }

                                abstract class ParameterInjector<T> implements MembersInjector<T> {
                                    final String name;
                                    ParameterInjector(String name) {
                                        this.name = name;
                                    }

                                    Object value() {
                                        return arguments.get(name);
                                    }
                                }

                                class FieldInjector<T> extends ParameterInjector<T> {
                                    final Field f;
                                    FieldInjector(Field f) {
                                        super(f.getName());
                                        this.f = f;
                                        f.setAccessible(true);
                                    }

                                    @Override
                                    public void injectMembers(T instance) {
                                        try {
                                            f.set(instance,value());
                                        } catch (IllegalAccessException e) {
                                            throw (Error)new IllegalAccessError(e.getMessage()).initCause(e);
                                        }
                                    }
                                }

                                class MethodInjector<T> extends ParameterInjector<T> {
                                    final Method m;
                                    MethodInjector(Method m) {
                                        super(m);
                                        Introspector.get
                                        this.m = m;
                                        m.setAccessible(true);
                                    }

                                    @Override
                                    public void injectMembers(T instance) {
                                        try {
                                            m.invoke(instance, value());
                                        } catch (IllegalAccessException e) {
                                            throw (Error)new IllegalAccessError(e.getMessage()).initCause(e);
                                        } catch (InvocationTargetException e) {
                                            throw new ProvisionException("Failed to set the parameter",e);
                                        }
                                    }
                                }
                            });
                        }
                    });
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

}
