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
import java.io.IOException;
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
        prepareInjector(context).injectMembers(this);
        return doStart(context);
    }

    /**
     * Creates an {@link Injector} that performs injection to {@link Inject} and {@link StepContextParameter}.
     */
    protected Injector prepareInjector(final StepContext context) {
        return Jenkins.getInstance().getInjector().createChildInjector(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bindListener(Matchers.any(),new TypeListener() {
                            @Override
                            public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
                                for (Field f : type.getRawType().getDeclaredFields()) {
                                    if (f.isAnnotationPresent(StepContextParameter.class)) {
                                        encounter.register(new FieldInjector<I>(f));
                                    }
                                }
                                for (Method m : type.getRawType().getDeclaredMethods()) {
                                    if (m.isAnnotationPresent(StepContextParameter.class)) {
                                        encounter.register(new MethodInjector<I>(m));
                                    }
                                }
                            }

                            abstract class ParameterInjector<T> implements MembersInjector<T> {
                                Object value(Class type) throws IOException, InterruptedException {
                                    return context.get(type);
                                }
                            }

                            class FieldInjector<T> extends ParameterInjector<T> {
                                final Field f;
                                FieldInjector(Field f) {
                                    this.f = f;
                                    f.setAccessible(true);
                                }

                                @Override
                                public void injectMembers(T instance) {
                                    try {
                                        f.set(instance,context.get(f.getType()));
                                    } catch (IllegalAccessException e) {
                                        throw (Error)new IllegalAccessError(e.getMessage()).initCause(e);
                                    } catch (InterruptedException e) {
                                        throw new ProvisionException("Failed to set a context parameter",e);
                                    } catch (IOException e) {
                                        throw new ProvisionException("Failed to set a context parameter",e);
                                    }
                                }
                            }

                            class MethodInjector<T> extends ParameterInjector<T> {
                                final Method m;
                                MethodInjector(Method m) {
                                    this.m = m;
                                    m.setAccessible(true);
                                }

                                @Override
                                public void injectMembers(T instance) {
                                    try {
                                        Class<?>[] types = m.getParameterTypes();
                                        Object[] args = new Object[types.length];
                                        for (int i = 0; i < args.length; i++) {
                                            args[i] = context.get(types[i]);
                                        }
                                        m.invoke(instance, args);
                                    } catch (IllegalAccessException e) {
                                        throw (Error)new IllegalAccessError(e.getMessage()).initCause(e);
                                    } catch (InvocationTargetException e) {
                                        throw new ProvisionException("Failed to set a context parameter",e);
                                    } catch (InterruptedException e) {
                                        throw new ProvisionException("Failed to set a context parameter",e);
                                    } catch (IOException e) {
                                        throw new ProvisionException("Failed to set a context parameter",e);
                                    }
                                }
                            }
                        });
                    }
                });
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
            Object[] args = buildArguments(arguments, names);

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
                            f.set(step,arguments.get(f.getName()));
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
                            m.invoke(step, buildArguments(arguments, names));
                        } catch (IllegalAccessException e) {
                            throw (Error)new IllegalAccessError(e.getMessage()).initCause(e);
                        } catch (InvocationTargetException e) {
                            throw (Error)new InstantiationError(e.getMessage()).initCause(e);
                        }
                    }
                }
            }
        }

        private Object[] buildArguments(Map<String, Object> arguments, String[] names) {
            Object[] args = new Object[names.length];
            for (int i = 0; i < args.length; i++) {
                args[i] = arguments.get(names[i]);
            }
            return args;
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
