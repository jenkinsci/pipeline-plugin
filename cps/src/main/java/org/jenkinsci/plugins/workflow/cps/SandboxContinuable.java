package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Outcome;
import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException;
import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.ClassLoaderWhitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.GroovySandbox;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.ProxyWhitelist;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;

import java.util.concurrent.Callable;

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
                    Throwable t = outcome.getAbnormal();
                    if (t instanceof RejectedAccessException) {
                        ScriptApproval.get().accessRejected((RejectedAccessException) t, ApprovalContext.create());
                    }
                    return outcome;
                }
            }, new ProxyWhitelist(new ClassLoaderWhitelist(thread.group.scriptClassLoader),CpsWhitelist.get()));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError(e);    // Callable doesn't throw anything
        }
    }
}
