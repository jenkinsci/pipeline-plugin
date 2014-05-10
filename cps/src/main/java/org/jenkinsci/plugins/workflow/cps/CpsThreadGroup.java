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
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.support.pickles.serialization.RiverWriter;
import groovy.lang.Closure;
import hudson.model.Result;
import jenkins.util.Timer;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static org.jenkinsci.plugins.workflow.cps.CpsFlowExecution.PROGRAM_STATE_SERIALIZATION;
import static java.util.logging.Level.*;

/**
 * List of {@link CpsThread}s that form a single {@link CpsFlowExecution}.
 *
 * <p>
 * To make checkpointing easy, only one {@link CpsThread} runs at any point in time.
 *
 * @author Kohsuke Kawaguchi
 */
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
     * Ensures only one thread executes {@link #run()} at any given time.
     */
    private transient AtmostOneTaskExecutor<?> runner;

    /**
     * "Exported" closures that are referenced by live {@link CpsStepContext}s.
     */
    public final Map<Integer,Closure> closures = new HashMap<Integer,Closure>();


    CpsThreadGroup(CpsFlowExecution execution) {
        this.execution = execution;
        setupRunner();
    }

    public CpsFlowExecution getExecution() {
        return execution;
    }

    private Object readResolve() {
        setupRunner();
        execution = CpsFlowExecution.PROGRAM_STATE_SERIALIZATION.get();
        assert execution!=null;
        return this;
    }

    private void setupRunner() {
        runner = new AtmostOneTaskExecutor<Void>(Timer.get(),new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    run();
                    return null;
                }
            });
    }

    public synchronized CpsThread addThread(Continuable program, FlowHead head, ContextVariableSet contextVariables) {
        CpsThread t = new CpsThread(this, iota++, program, head, contextVariables);
        threads.put(t.id, t);
        return t;
    }

    public CpsThread getThread(int id) {
        return threads.get(id);
    }

    public synchronized BodyReference export(Closure body) {
        if (body==null)     return null;
        int id = iota++;
        closures.put(id, body);
        return new StaticBodyReference(id,body);
    }

    public synchronized void unexport(BodyReference ref) {
        if (ref==null)      return;
        closures.remove(ref.id);
    }

    public Future<?> scheduleRun() {
        return runner.submit();
    }

    /**
     * Run all runnable threads as much as possible.
     *
     * This method gets executed sequentially through {@link #scheduleRun()}
     */
    private synchronized void run() throws IOException {
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
                        LOGGER.fine("completed "+t);

                        threads.remove(t.id);
                        if (threads.isEmpty()) {
                            execution.onProgramEnd(o);
                        }
                    }

                    changed = true;
                }
            }

            doneSomeWork |= changed;
        } while(changed);

        if (doneSomeWork) {
            saveProgram();
            LOGGER.log(FINE, "program state saved");
        }
    }

    /**
     * Persists the current state of {@link CpsThreadGroup}.
     */
    public synchronized void saveProgram() throws IOException {
        saveProgram(execution.getProgramDataFile());
    }

    public synchronized void saveProgram(File f) throws IOException {
        CpsFlowExecution old = PROGRAM_STATE_SERIALIZATION.get();
        PROGRAM_STATE_SERIALIZATION.set(execution);

        try {
            // TODO: write atomically
            RiverWriter w = new RiverWriter(f);
            try {
                w.writeObject(this);
            } finally {
                w.close();
            }
        } finally {
            PROGRAM_STATE_SERIALIZATION.set(old);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CpsThreadGroup.class.getName());

    private static final long serialVersionUID = 1L;
}
