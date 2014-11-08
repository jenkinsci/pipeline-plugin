package org.jenkinsci.plugins.workflow.steps;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.MembersInjector;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.google.inject.util.Providers;
import jenkins.model.Jenkins;
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
    public final Step newInstance(Map<String,Object> arguments) throws Exception {
        if (arguments.keySet().equals(Collections.singleton(KEY_VALUE))) {
            String[] names = new ClassDescriptor(clazz).loadConstructorParamNames();
            if (names.length == 1) {
                arguments = Collections.singletonMap(names[0], arguments.get(KEY_VALUE));
            }
        }
        return DescribableHelper.instantiate(clazz, arguments);
    }


    @Override public Map<String,Object> defineArguments(Step step) {
        Map<String,Object> arguments = DescribableHelper.uninstantiate(step);
        String[] names = new ClassDescriptor(step.getClass()).loadConstructorParamNames();
        if (names.length == 1 && arguments.keySet().equals(Collections.singleton(names[0]))) {
            arguments = Collections.singletonMap(KEY_VALUE, arguments.get(names[0]));
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


    /**
     * Creates an {@link Injector} that performs injection to {@link Inject} and {@link StepContextParameter}.
     */
    protected Injector prepareInjector(final StepContext context, @Nullable final Step step) {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            throw new IllegalStateException("Jenkins is not running");
        }
        return j.getInjector().createChildInjector(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(StepContext.class).toInstance(context);

                        // make the outer 'this' object available at arbitrary super type of the actual concrete type
                        // this will allow Step to subtype another Step and work as expected
                        for (Class c=clazz; c!=Step.class; c=c.getSuperclass()) {
                            if (step==null)
                                bind(c).toProvider(NULL_PROVIDER);
                            else
                                bind(c).toInstance(step);
                        }

                        bindListener(Matchers.any(), new TypeListener() {
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
                                        f.set(instance, context.get(f.getType()));
                                    } catch (IllegalAccessException e) {
                                        throw (Error) new IllegalAccessError(e.getMessage()).initCause(e);
                                    } catch (InterruptedException e) {
                                        throw new ProvisionException("Failed to set a context parameter", e);
                                    } catch (IOException e) {
                                        throw new ProvisionException("Failed to set a context parameter", e);
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
                                        throw (Error) new IllegalAccessError(e.getMessage()).initCause(e);
                                    } catch (InvocationTargetException e) {
                                        throw new ProvisionException("Failed to set a context parameter", e);
                                    } catch (InterruptedException e) {
                                        throw new ProvisionException("Failed to set a context parameter", e);
                                    } catch (IOException e) {
                                        throw new ProvisionException("Failed to set a context parameter", e);
                                    }
                                }
                            }
                        });
                    }
        });
    }

    private static final Provider<Object> NULL_PROVIDER = Providers.of(null);
}
