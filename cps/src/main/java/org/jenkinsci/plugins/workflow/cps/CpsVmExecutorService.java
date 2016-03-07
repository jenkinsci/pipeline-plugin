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
                String oldThreadName = setUp();
                try {
                    r.run();
                } finally {
                    tearDown(oldThreadName);
                }
            }
        };
    }

    @Override
    protected <V> Callable<V> wrap(final Callable<V> r) {
        return new Callable<V>() {
            @Override
            public V call() throws Exception {
                String oldThreadName = setUp();
                try {
                    return r.call();
                } finally {
                    tearDown(oldThreadName);
                }
            }
        };
    }

    private String setUp() {
        CpsFlowExecution execution = cpsThreadGroup.getExecution();
        ACL.impersonate(execution.getAuthentication());
        CURRENT.set(cpsThreadGroup);
        cpsThreadGroup.busy = true;
        Thread t = Thread.currentThread();
        String oldThreadName = t.getName();
        t.setName("Running " + execution);
        return oldThreadName;
    }

    private void tearDown(String oldThreadName) {
        CURRENT.set(null);
        cpsThreadGroup.busy = false;
        Thread.currentThread().setName(oldThreadName);
    }

    static ThreadLocal<CpsThreadGroup> CURRENT = new ThreadLocal<CpsThreadGroup>();
}
