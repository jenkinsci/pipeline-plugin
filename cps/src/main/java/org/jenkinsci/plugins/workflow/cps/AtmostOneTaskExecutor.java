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

import com.google.common.util.concurrent.SettableFuture;
import hudson.remoting.AtmostOneThreadExecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * TODO 1.562+ https://trello.com/c/WCMEn3VO/35-atmostonetaskexecutor
 */
class AtmostOneTaskExecutor<V> {
    /**
     * The actual executor that executes {@link #task}
     */
    private final ExecutorService base;

    /**
     * Task to be executed.
     */
    private final Callable<V> task;

    /**
     * If a task is already submitted and pending execution, non-null.
     * Guarded by "synchronized(this)"
     */
    private SettableFuture<V> pending;

    private SettableFuture<V> inprogress;

    public AtmostOneTaskExecutor(ExecutorService base, Callable<V> task) {
        this.base = base;
        this.task = task;
    }

    public AtmostOneTaskExecutor(Callable<V> task) {
        this(new AtmostOneThreadExecutor(),task);
    }

    public synchronized Future<V> submit() {
        if (pending==null) {
            pending = SettableFuture.create();
            maybeRun();
        }
        return pending;
    }

    /**
     * If {@link #pending} is non-null (meaning someone requested the task to be kicked),
     * but {@link #inprogress} is null (meaning none is executing right now),
     * get one going.
     */
    private synchronized void maybeRun() {
        if (inprogress==null && pending!=null) {
            base.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    synchronized (AtmostOneTaskExecutor.this) {
                        // everyone who submits after this should form a next batch
                        inprogress = pending;
                        pending = null;
                    }

                    try {
                        inprogress.set(task.call());
                    } catch (Throwable t) {
                        inprogress.setException(t);
                    } finally {
                        synchronized (AtmostOneTaskExecutor.this) {
                            // if next one is pending, get that scheduled
                            inprogress = null;
                            maybeRun();
                        }
                    }
                    return null;
                }
            });
        }
    }
}
