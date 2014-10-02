package org.jenkinsci.plugins.workflow.cps;

import hudson.security.ACL;

/**
 * Thread that executes {@link CpsThreadGroup} and handles all its state updates.
 *
 * @author Kohsuke Kawaguchi
 * @see CpsVmThreadOnly
 */
class CpsVmThread extends Thread {
    final CpsThreadGroup threadGroup;

    public CpsVmThread(CpsThreadGroup threadGroup, Runnable target) {
        super(target, "CPS VM execution thread: " + threadGroup.getExecution().toString());
        this.threadGroup = threadGroup;
    }

    @Override public void run() {
        ACL.impersonate(threadGroup.getExecution().getAuthentication());
        super.run();
    }

    /*package*/ static CpsVmThread current() {
        Thread t = Thread.currentThread();
        if (t instanceof CpsVmThread) {
            return (CpsVmThread) t;
        }
        return null;
    }

}
