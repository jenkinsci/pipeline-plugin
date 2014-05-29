package org.jenkinsci.plugins.workflow.cps;

import groovy.lang.Closure;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;

/**
 * "Built-in" functions for CPS groovy script.
 *
 * These static methods get auto-imported into the script during compilation.
 *
 * @author Kohsuke Kawaguchi
 */
@PersistIn(NONE)
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
        return new DSL(CpsThread.current().getExecution().getOwner());
    }

    private static Object[] asArray(Object... args) {
        return args;
    }
}
