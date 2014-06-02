package org.jenkinsci.plugins.workflow.steps;

import com.google.inject.MembersInjector;

import java.lang.reflect.Field;

/**
 * @author Kohsuke Kawaguchi
 */
class ParameterInjector<T> implements MembersInjector<T> {
    private final Field f;
    public ParameterInjector(Field f) {
        this.f = f;
        f.setAccessible(true);
    }

    @Override
    public void injectMembers(T instance) {
        // TODO
        throw new UnsupportedOperationException();
    }
}
