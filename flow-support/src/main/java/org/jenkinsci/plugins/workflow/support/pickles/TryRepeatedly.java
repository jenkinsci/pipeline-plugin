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

package org.jenkinsci.plugins.workflow.support.pickles;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import jenkins.util.Timer;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.CheckForNull;

/**
 * {@link ListenableFuture} that promises a value that needs to be periodically tried.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class TryRepeatedly<V> extends AbstractFuture<V> {
    private final int seconds;
    private ScheduledFuture<?> next;

    protected TryRepeatedly(int seconds) {
        this.seconds = seconds;
        tryLater();
    }

    private void tryLater() {
        // TODO log a warning if trying for too long; probably Pickle.rehydrate should be given a TaskListener to note progress

        if (isCancelled())      return;

        next = Timer.get().schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    V v = tryResolve();
                    if (v == null)
                        tryLater();
                    else
                        set(v);
                } catch (Throwable t) {
                    setException(t);
                }
            }
        }, seconds, TimeUnit.SECONDS);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (next!=null)
            next.cancel(mayInterruptIfRunning);
        return super.cancel(mayInterruptIfRunning);
    }

    /**
     * This method is called periodically to attempt to resolve the value that this future promises.
     *
     * @return
     *      null to retry this at a later moment.
     * @throws Exception
     *      Any exception thrown will cause the future to fail.
     */
    protected abstract @CheckForNull V tryResolve() throws Exception;
}
