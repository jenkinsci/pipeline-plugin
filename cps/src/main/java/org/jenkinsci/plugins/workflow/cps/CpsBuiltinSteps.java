package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Outcome;
import groovy.lang.Closure;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * Executes the body closure up to N times until it succeeds.
     *
     * <pre>
     * retry (3) {
     *     ... computation that can fail ...
     * }
     * </pre>
     */
    public static void retry(int n, Closure body) {
        dsl().invokeMethod("retry", asArray(n, body));
    }

    public static Map<String,Outcome> parallel(Map<String,Closure> subflows) {
        return (Map<String,Outcome>)dsl().invokeMethod("parallel", asArray(subflows));
    }

    /**
     * Version of {@code parallel} that doesn't have any branch names.
     *
     * The lack of branch name hurts visualization, so it's preferrable to give them if you can.
     */
    public static Map<String,Outcome> parallel(Closure... subflows) {
        return parallel(Arrays.asList(subflows));
    }

    public static Map<String,Outcome> parallel(Collection<? extends Closure> subflows) {
        Map<String,Closure> flows = new HashMap<String, Closure>();
        int i=1;
        for (Closure c : subflows) {
            flows.put("flow"+(i++),c);
        }
        return parallel(flows);
    }

    private static DSL dsl() {
        return new DSL(CpsThread.current().getExecution().getOwner());
    }

    private static Object[] asArray(Object... args) {
        return args;
    }
}
