/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Outcome;
import hudson.util.AtomicFileWriter;
import jenkins.util.AtmostOneTaskExecutor;
import groovy.lang.Closure;
import hudson.model.Result;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.support.pickles.serialization.RiverWriter;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import static java.util.logging.Level.*;
import static org.jenkinsci.plugins.workflow.cps.CpsFlowExecution.*;
import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;

/**
 * List of {@link CpsThread}s that form a single {@link CpsFlowExecution}.
 *
 * <p>
 * To make checkpointing easy, only one {@link CpsThread} runs at any point in time.
 *
 * @author Kohsuke Kawaguchi
 */
@PersistIn(PROGRAM)
public final class CpsThreadGroup implements Serializable {
    /**
     * {@link CpsThreadGroup} always belong to the same {@link CpsFlowExecution}.
     *
     * {@link CpsFlowExecution} and {@link CpsThreadGroup} persist separately,
     * so this field is not persisted, but get fixed up in {@link #readResolve()}
     */
    private /*almost final*/ transient CpsFlowExecution execution;

    /**
     * All the member threads by their {@link CpsThread#id}
     */
    final Map<Integer,CpsThread> threads = new HashMap<Integer, CpsThread>();

    /**
     * Unique thread ID generator.
     */
    private int iota;

    /**
     * Ensures only one thread updates CPS VM state at any given time
     * by queueing such tasks in here.
     */
    transient ExecutorService runner;

    /**
     * "Exported" closures that are referenced by live {@link CpsStepContext}s.
     */
    public final Map<Integer,Closure> closures = new HashMap<Integer,Closure>();

    CpsThreadGroup(CpsFlowExecution execution) {
        this.execution = execution;
        setupTransients();
    }

    public CpsFlowExecution getExecution() {
        return execution;
    }

    private Object readResolve() {
        execution = CpsFlowExecution.PROGRAM_STATE_SERIALIZATION.get();
        setupTransients();
        assert execution!=null;
        return this;
    }

    private void setupTransients() {
        runner = Executors.newSingleThreadExecutor(
                new ThreadFactory() {
                    @Override
                    public CpsVmThread newThread(Runnable r) {
                        return new CpsVmThread(CpsThreadGroup.this, r);
                    }
                });
    }

    @CpsVmThreadOnly
    public CpsThread addThread(Continuable program, FlowHead head, ContextVariableSet contextVariables) {
        assertVmThread();
        CpsThread t = new CpsThread(this, iota++, program, head, contextVariables);
        threads.put(t.id, t);
        return t;
    }

    /**
     * Ensures that the current thread is the correct {@link CpsVmThread}
     *
     * @see CpsVmThreadOnly
     */
    private void assertVmThread() {
        assert Thread.currentThread() instanceof CpsVmThread;
        assert ((CpsVmThread)Thread.currentThread()).threadGroup==this;
    }

    public CpsThread getThread(int id) {
        return threads.get(id);
    }

    @CpsVmThreadOnly("root")
    public BodyReference export(Closure body) {
        assertVmThread();
        if (body==null)     return null;
        int id = iota++;
        closures.put(id, body);
        return new StaticBodyReference(id,body);
    }

    @CpsVmThreadOnly("root")
    public void unexport(BodyReference ref) {
        assertVmThread();
        if (ref==null)      return;
        closures.remove(ref.id);
    }

    /**
     * Schedules the execution of all the runnable threads.
     */
    public Future<?> scheduleRun() {
        final Future<Future<?>> f = runner.submit(new Callable<Future<?>>() {
            public Future<?> call() throws Exception {
                run();
                // we ensure any tasks submitted during run() will complete before we declare us complete
                // those include things like notifying listeners or updating various other states
                // runner is a single-threaded queue, so running a no-op and waiting for its completion
                // ensures that everything submitted in front of us has finished.

                return runner.submit(new Runnable() {
                    @Override
                    public void run() {
                        if (threads.isEmpty())
                            runner.shutdown();
                    }
                });
            }
        });

        // unfortunately that means we have to wait for Future of Future,
        // so we need a rather unusual implementation of Future to hide that behind the scene.
        return new Future<Object>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (!f.isDone())
                    return f.cancel(mayInterruptIfRunning);

                try {
                    return f.get().cancel(mayInterruptIfRunning);
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                } catch (ExecutionException e) {
                    return false;
                }
            }

            @Override
            public boolean isCancelled() {
                if (f.isCancelled())    return true;
                if (!f.isDone())        return false;

                try {
                    return f.get().isCancelled();
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                } catch (ExecutionException e) {
                    return false;
                }
            }

            @Override
            public boolean isDone() {
                if (!f.isDone())    return false;

                try {
                    return f.get().isDone();
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                } catch (ExecutionException e) {
                    return false;
                }
            }

            @Override
            public Object get() throws InterruptedException, ExecutionException {
                return f.get().get();
            }

            @Override
            public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                // FIXME: this ends up waiting up to 2x
                return f.get(timeout,unit).get(timeout,unit);
            }
        };
    }

    /**
     * Run all runnable threads as much as possible.
     */
    @CpsVmThreadOnly("root")
    private void run() throws IOException {
        boolean doneSomeWork = false;
        boolean changed;    // used to see if we need to loop over
        do {
            changed = false;
            for (CpsThread t : threads.values().toArray(new CpsThread[threads.size()])) {
                if (t.isRunnable()) {
                    Outcome o = t.runNextChunk();
                    if (o.isFailure()) {
                        assert !t.isAlive();    // failed thread is non-resumable

                        // workflow produced an exception
                        execution.setResult(Result.FAILURE);
                        t.head.get().addAction(new ErrorAction(o.getAbnormal()));
                    }

                    if (!t.isAlive()) {
                        LOGGER.fine("completed " + t);

                        threads.remove(t.id);
                        if (threads.isEmpty()) {
                            execution.onProgramEnd(o);
                        }
                    }

                    changed = true;
                }
            }

            doneSomeWork |= changed;
        } while (changed);

        if (doneSomeWork) {
            saveProgram();
            LOGGER.log(FINE, "program state saved");
        }
    }

    /**
     * Notifies listeners of the new {@link FlowHead}.
     *
     * The actual call happens later from a place who owns no lock on any of the CPS objects to avoid deadlock.
     * See https://trello.com/c/7aTFYWM5/26-intermittent-deadlock
     */
    @CpsVmThreadOnly
    /*package*/ void notifyNewHead(final FlowNode head) {
        assertVmThread();
        runner.execute(new Runnable() {
            public void run() {
                execution.notifyListeners(head);
            }
        });
    }

    /**
     * Persists the current state of {@link CpsThreadGroup}.
     */
    @CpsVmThreadOnly
    void saveProgram() throws IOException {
        saveProgram(execution.getProgramDataFile());
    }

    @CpsVmThreadOnly
    void saveProgram(File f) throws IOException {
        File dir = f.getParentFile();
        File tmpFile = File.createTempFile("atomic",null, dir);

        assertVmThread();

        CpsFlowExecution old = PROGRAM_STATE_SERIALIZATION.get();
        PROGRAM_STATE_SERIALIZATION.set(execution);

        try {
            // TODO: write atomically
            RiverWriter w = new RiverWriter(tmpFile, execution.getOwner());
            try {
                w.writeObject(this);
            } finally {
                w.close();
            }
            f.delete();
            tmpFile.renameTo(f);
        } catch (RuntimeException e) {
            throw new IOException("Failed to persist "+f,e);
        } catch (IOException e) {
            throw new IOException("Failed to persist "+f,e);
        } finally {
            PROGRAM_STATE_SERIALIZATION.set(old);
            tmpFile.delete();
        }
    }

    public Future<Void> scheduleSaveProgram() {
        return runner.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                saveProgram();
                return null;
            }
        });
    }

    private static final Logger LOGGER = Logger.getLogger(CpsThreadGroup.class.getName());

    private static final long serialVersionUID = 1L;

    /**
     * CPS transformed program runs entirely inside a program execution thread.
     * If we are in that thread executing {@link CpsThreadGroup}, this method returns non-null.
     */
    @CpsVmThreadOnly
    /*package*/ static CpsThreadGroup current() {
        return CpsVmThread.current().threadGroup;
    }

    private static final Runnable NOOP = new Runnable() {
        public void run() {
        }
    };
}
