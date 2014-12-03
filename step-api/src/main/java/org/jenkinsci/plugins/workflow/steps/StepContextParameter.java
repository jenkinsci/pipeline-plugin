package org.jenkinsci.plugins.workflow.steps;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects value from {@link StepContext#get(Class)}.
 *
 * Used on methods and fields of the subtype of {@link AbstractStepExecutionImpl}.
 * {@link AbstractStepImpl#start} and {@link AbstractStepExecutionImpl#onResume} will inject context variables
 * to those fields.
 *
 * @author Kohsuke Kawaguchi
 * @see AbstractStepImpl
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD,ElementType.FIELD})
@Documented
public @interface StepContextParameter {
}
