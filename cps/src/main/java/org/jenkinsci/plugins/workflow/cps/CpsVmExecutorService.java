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
                ThreadContext context = setUp();
                try {
                    r.run();
                } finally {
                    tearDown(context);
                }
            }
        };
    }

    @Override
    protected <V> Callable<V> wrap(final Callable<V> r) {
        return new Callable<V>() {
            @Override
            public V call() throws Exception {
                ThreadContext context = setUp();
                try {
                    return r.call();
                } finally {
                    tearDown(context);
                }
            }
        };
    }

    private static class ThreadContext {
        final Thread thread;
        final String name;
        final ClassLoader classLoader;
        ThreadContext(Thread thread) {
            this.thread = thread;
            this.name = thread.getName();
            this.classLoader = thread.getContextClassLoader();
        }
        void restore() {
            thread.setName(name);
            thread.setContextClassLoader(classLoader);
        }
    }

    private ThreadContext setUp() {
        CpsFlowExecution execution = cpsThreadGroup.getExecution();
        ACL.impersonate(execution.getAuthentication());
        CURRENT.set(cpsThreadGroup);
        cpsThreadGroup.busy = true;
        Thread t = Thread.currentThread();
        ThreadContext context = new ThreadContext(t);
        t.setName("Running " + execution);
        assert cpsThreadGroup.getExecution() != null;
        if (cpsThreadGroup.getExecution().getShell() != null) {
            assert cpsThreadGroup.getExecution().getShell().getClassLoader() != null;
            t.setContextClassLoader(cpsThreadGroup.getExecution().getShell().getClassLoader());
        }
        return context;
    }

    private void tearDown(ThreadContext context) {
        CURRENT.set(null);
        cpsThreadGroup.busy = false;
        context.restore();
    }

    static ThreadLocal<CpsThreadGroup> CURRENT = new ThreadLocal<CpsThreadGroup>();
}
