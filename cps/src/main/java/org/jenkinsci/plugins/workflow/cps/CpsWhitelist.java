package org.jenkinsci.plugins.workflow.cps;

import org.codehaus.groovy.runtime.GStringImpl;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.AbstractWhitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.ProxyWhitelist;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import jenkins.model.Jenkins;

/**
 * {@link Whitelist} implementation for CPS flow execution.
 *
 * @author Kohsuke Kawaguchi
 */
class CpsWhitelist extends AbstractWhitelist {
    private CpsWhitelist() {}

    @Override
    public boolean permitsMethod(Method method, Object receiver, Object[] args) {
        if (receiver instanceof CpsScript) {
            String name = method.getName();
            if (name.equals("invokeMethod")) {
                // CpsScript dispatches to the DSL class
                return true;
            }
            if (name.equals("evaluate")) {
                // evaluate() family of methods are reimplemented in CpsScript for safe manner
                // but we can't allow arbitrary Script.evaluate() calls as that will escape sandbox
                return true;
            }
            if (name.equals("println") || name.equals("print") || name.equals("printf")) {
                // These are just aliases for EchoStep.
                return true;
            }
            if (name.equals("getProperty") && args.length == 1 && args[0] instanceof String) {
                for (GlobalVariable v : GlobalVariable.ALL) {
                    if (v.getName().equals(args[0])) {
                        return true;
                    }
                }
            }
        }
        if (receiver instanceof DSL && method.getName().equals("invokeMethod")) {
            return true;
        }
        // TODO JENKINS-24982: it would be nice if AnnotatedWhitelist accepted @Whitelisted on an override
        if (receiver instanceof EnvActionImpl) {
            String name = method.getName();
            if (name.equals("getProperty") || name.equals("setProperty")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean permitsConstructor(Constructor<?> constructor, Object[] args) {
        if (constructor.getDeclaringClass()== GStringImpl.class)
            return true;

        return false;
    }

    @Override
    public boolean permitsStaticMethod(Method method, Object[] args) {
        // type coercive cast. In particular, this is used to build GString. See com.cloudbees.groovy.cps.Builder.gstring
        if (method.getDeclaringClass()==ScriptBytecodeAdapter.class && method.getName().equals("asType"))
            return true;

        return false;
    }

    /**
     * Stuff we whitelist specifically for CPS, with the rest of the installed rules combined.
     */
    private static final Map<Jenkins,Whitelist> wrappedByJenkins = new WeakHashMap<Jenkins,Whitelist>();

    public static synchronized Whitelist get() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            return new ProxyWhitelist();
        }
        Whitelist wrapped = wrappedByJenkins.get(j);
        if (wrapped == null) {
            wrapped = new ProxyWhitelist(new CpsWhitelist(), Whitelist.all());
            wrappedByJenkins.put(j, wrapped);
        }
        return wrapped;
    }
}
