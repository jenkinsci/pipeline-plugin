package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Outcome;
import java.util.concurrent.Callable;
import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException;
import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.GroovySandbox;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;

/**
 * {@link Continuable} that executes code inside sandbox execution.
 *
 * @author Kohsuke Kawaguchi
 */
class SandboxContinuable extends Continuable {
    SandboxContinuable(Continuable src) {
        super(src);
    }

    @Override
    public Outcome run0(final Outcome cn) {
        try {
            return GroovySandbox.runInSandbox(new Callable<Outcome>() {
                @Override
                public Outcome call() {
                    Outcome outcome = SandboxContinuable.super.run0(cn);
                    Throwable t = outcome.getAbnormal();
                    if (t instanceof RejectedAccessException) {
                        ScriptApproval.get().accessRejected((RejectedAccessException) t, ApprovalContext.create());
                    }
                    return outcome;
                }
            }, Whitelist.all());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError(e);    // Callable doesn't throw anything
        }
    }
}
