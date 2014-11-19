package org.jenkinsci.plugins.workflow.cps;

import groovy.lang.GroovyClassLoader;
import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Allow any calls into the scripted compiled in the sandbox.
 *
 * IOW, allow the user to call his own methods.
 *
 * @author Kohsuke Kawaguchi
 */
class GroovyClassLoaderWhitelist extends Whitelist {
    private final ClassLoader scriptLoader;

    public GroovyClassLoaderWhitelist(GroovyClassLoader scriptLoader) {
        this.scriptLoader = scriptLoader;
    }

    private boolean permits(Class<?> declaringClass) {
        ClassLoader cl = declaringClass.getClassLoader();
        if (cl instanceof GroovyClassLoader.InnerLoader) {
            return cl.getParent()==scriptLoader;
        }
        return cl == scriptLoader;
    }

    @Override public boolean permitsMethod(Method method, Object receiver, Object[] args) {
        return permits(method.getDeclaringClass());
    }

    @Override public boolean permitsConstructor(Constructor<?> constructor, Object[] args) {
        return permits(constructor.getDeclaringClass());
    }

    @Override public boolean permitsStaticMethod(Method method, Object[] args) {
        return permits(method.getDeclaringClass());
    }

    @Override public boolean permitsFieldGet(Field field, Object receiver) {
        return permits(field.getDeclaringClass());
    }

    @Override public boolean permitsFieldSet(Field field, Object receiver, Object value) {
        return permits(field.getDeclaringClass());
    }

    @Override public boolean permitsStaticFieldGet(Field field) {
        return permits(field.getDeclaringClass());
    }

    @Override public boolean permitsStaticFieldSet(Field field, Object value) {
        return permits(field.getDeclaringClass());
    }
}
