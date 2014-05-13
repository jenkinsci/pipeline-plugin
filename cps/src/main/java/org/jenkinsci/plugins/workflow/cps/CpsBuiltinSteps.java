package org.jenkinsci.plugins.workflow.cps;

import groovy.lang.Closure;

import java.util.Arrays;

import static java.util.Arrays.asList;

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
        return (Integer)dsl().invokeMethod("sh", asList(args));
    }

    public static void retry(int n, Closure body) {
        dsl().invokeMethod("retry", asList(n, body));
    }

    private static DSL dsl() {
        return new DSL(CpsThread.current().group.getExecution().getOwner());
    }
}
