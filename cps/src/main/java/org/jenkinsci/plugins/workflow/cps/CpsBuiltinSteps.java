package org.jenkinsci.plugins.workflow.cps;

import groovy.lang.Closure;

/**
 * "Built-in" functions for CPS groovy script.
 *
 * These static methods get auto-imported into the script during compilation.
 *
 * @author Kohsuke Kawaguchi
 */
public class CpsBuiltinSteps {
    /**
     * Executes a shell script in the current workspace.
     */
    public static int sh(Object... args) {
        return (Integer)dsl().invokeMethod("sh", asArray(args));
    }

    public static void retry(int n, Closure body) {
        dsl().invokeMethod("retry", asArray(n, body));
    }

    private static DSL dsl() {
        return new DSL(CpsThread.current().group.getExecution().getOwner());
    }

    private static Object[] asArray(Object... args) {
        return args;
    }
}
