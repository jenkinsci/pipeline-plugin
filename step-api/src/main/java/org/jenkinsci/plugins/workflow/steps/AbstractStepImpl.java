package org.jenkinsci.plugins.workflow.steps;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.MembersInjector;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.inject.Inject;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Partial convenient step implementation.
 *
 * <h2>Parameter injection</h2>
 * <p>
 * {@link Step} implementations are expected to follow the usual GUI-instantiable {@link Describable} pattern.
 * {@link AbstractStepImpl} comes with {@linkplain AbstractStepDescriptorImpl a partial implementation of StepDescriptor}
 * that automatically instantiate a Step subtype and perform {@link DataBoundConstructor}/{@link DataBoundSetter}
 * injections just like {@link Descriptor#newInstance(StaplerRequest, JSONObject)} does from JSON.
 *
 * <p>
 * In addition, fields and setter methods annotated with {@link StepContextParameter} will get its value
 * injected from {@link StepContext}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractStepImpl extends Step {
    @Override
    public StepExecution start(StepContext context) throws Exception {
        return prepareInjector(context).getInstance(((AbstractStepDescriptorImpl)getDescriptor()).getExecutionType());
    }

    /**
     * Creates an {@link Injector} that performs injection to {@link Inject} and {@link StepContextParameter}.
     */
    protected Injector prepareInjector(final StepContext context) {
        return Jenkins.getInstance().getInjector().createChildInjector(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(StepContext.class).toInstance(context);
                        bind(Step.class).toInstance(AbstractStepImpl.this);
                        bind((Class)AbstractStepImpl.this.getClass()).toInstance(AbstractStepImpl.this);

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
}
