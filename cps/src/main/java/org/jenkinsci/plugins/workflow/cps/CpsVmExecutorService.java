package org.jenkinsci.plugins.workflow.cps;

import hudson.model.Computer;
import hudson.remoting.SingleLaneExecutorService;
import hudson.security.ACL;
import jenkins.util.InterceptingExecutorService;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 * {@link ExecutorService} for running CPS VM.
 *
 * @author Kohsuke Kawaguchi
 * @see CpsVmThreadOnly
 */
class CpsVmExecutorService extends InterceptingExecutorService {
    private CpsThreadGroup cpsThreadGroup;

    public CpsVmExecutorService(CpsThreadGroup cpsThreadGroup) {
        super(new SingleLaneExecutorService(Computer.threadPoolForRemoting));
        this.cpsThreadGroup = cpsThreadGroup;
    }

    @Override
    protected Runnable wrap(final Runnable r) {
        return new Runnable() {
            @Override
            public void run() {
                setUp();
                try {
                    r.run();
                } finally {
                    tearDown();
                }
            }
        };
    }

    @Override
    protected <V> Callable<V> wrap(final Callable<V> r) {
        return new Callable<V>() {
            @Override
            public V call() throws Exception {
                setUp();
                try {
                    return r.call();
                } finally {
                    tearDown();
                }
            }
        };
    }

    private void setUp() {
        ACL.impersonate(cpsThreadGroup.getExecution().getAuthentication());
        CURRENT.set(cpsThreadGroup);
        cpsThreadGroup.busy = true;
    }

    private void tearDown() {
        CURRENT.set(null);
        cpsThreadGroup.busy = false;
    }

    static ThreadLocal<CpsThreadGroup> CURRENT = new ThreadLocal<CpsThreadGroup>();
}
