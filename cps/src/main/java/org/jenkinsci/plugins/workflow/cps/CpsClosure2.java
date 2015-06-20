package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.impl.CpsClosure;
import org.codehaus.groovy.runtime.DefaultGroovyStaticMethods;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.util.List;

/**
 * {@link CpsClosure} that intercepts the {@code sleep} call so that it gets handled via SleepStep,
 * instead of {@link DefaultGroovyStaticMethods#sleep(Object, long)} that Groovy adds to {@code Object}.
 *
 * <p>
 * We had to do this because we made a mistake of having the 'sleep' step pick the same name as
 * a method defined on {@code Object}. Granted, it is a method added by Groovy, not by JDK, but the end result
 * is still the same, and the consequence is as severe as trying to override {@code hashCode()} method
 * and use it for something completely different. We ended up doing this because when we did it, a bug masked
 * the severity of the problem. In hindsight, we should have thought twice before adding a work around
 * like {@link CpsScript#sleep(long)}.
 *
 * @author Kohsuke Kawaguchi
 */
public class CpsClosure2 extends CpsClosure {
    public CpsClosure2(Object owner, Object thisObject, List<String> parameters, Block body, Env capture) {
        super(owner, thisObject, parameters, body, capture);
    }

    /**
     * @see CpsScript#sleep(long)
     */
    public Object sleep(long arg) {
        return InvokerHelper.invokeMethod(getOwner(), "sleep", arg);
    }
}
