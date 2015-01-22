package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Outcome;
import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.GroovySandbox;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.ProxyWhitelist;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;

import java.util.concurrent.Callable;
import javax.annotation.CheckForNull;

/**
 * {@link Continuable} that executes code inside sandbox execution.
 *
 * @author Kohsuke Kawaguchi
 */
class SandboxContinuable extends Continuable {
    private final CpsThread thread;

    SandboxContinuable(Continuable src, CpsThread thread) {
        super(src);
        this.thread = thread;
    }

    @Override
    public Outcome run0(final Outcome cn) {
        try {
            return GroovySandbox.runInSandbox(new Callable<Outcome>() {
                @Override
                public Outcome call() {
                    Outcome outcome = SandboxContinuable.super.run0(cn);
                    RejectedAccessException x = findRejectedAccessException(outcome.getAbnormal());
                    if (x != null) {
                        ScriptApproval.get().accessRejected(x, ApprovalContext.create());
                    }
                    return outcome;
                }
            }, new ProxyWhitelist(new GroovyClassLoaderWhitelist(thread.group.getExecution().getShell().getClassLoader()),CpsWhitelist.get()));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError(e);    // Callable doesn't throw anything
        }
    }

    private static @CheckForNull RejectedAccessException findRejectedAccessException(@CheckForNull Throwable t) {
        if (t == null) {
            return null;
        } else if (t instanceof RejectedAccessException) {
            return (RejectedAccessException) t;
        } else {
            return findRejectedAccessException(t.getCause());
        }
    }

}
