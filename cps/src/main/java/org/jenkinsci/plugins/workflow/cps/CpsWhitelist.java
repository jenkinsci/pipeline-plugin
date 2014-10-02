package org.jenkinsci.plugins.workflow.cps;


import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.AbstractWhitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.ProxyWhitelist;

/**
 * {@link Whitelist} used when executing CPS sandbox.
 *
 * @author Kohsuke Kawaguchi
 */
class CpsWhitelist {
    static final Whitelist INSTANCE = new ProxyWhitelist(Whitelist.all(), new Impl());

    private static class Impl extends AbstractWhitelist {
    }
}
