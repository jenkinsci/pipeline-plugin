package org.jenkinsci.plugins.workflow.cps.persistence;

import hudson.model.Run;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * Indicates that this class gets persisted in the {@code build.xml} as a part of
 * {@link Run} through XStream.
 *
 * @author Kohsuke Kawaguchi
 */
@Retention(SOURCE)
@Target(TYPE)
@Inherited
public @interface PersistIn {
    PersistenceContext value();
}
