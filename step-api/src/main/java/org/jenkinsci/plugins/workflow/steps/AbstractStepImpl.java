package org.jenkinsci.plugins.workflow.steps;

import com.google.inject.Injector;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nullable;
import javax.inject.Inject;

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

    /** Constructs a step execution automatically according to {@link AbstractStepDescriptorImpl#getExecutionType}. */
    @Override public final StepExecution start(StepContext context) throws Exception {
        AbstractStepDescriptorImpl d = (AbstractStepDescriptorImpl) getDescriptor();
        return prepareInjector(context, this).getInstance(d.getExecutionType());
    }

    /**
     * Creates an {@link Injector} that performs injection to {@link Inject} and {@link StepContextParameter}.
     */
    protected static Injector prepareInjector(final StepContext context, @Nullable final Step step) {
        return Jenkins.getActiveInstance().getInjector().createChildInjector(new ContextParameterModule(step,context));
    }
}
