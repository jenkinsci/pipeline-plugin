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

package org.jenkinsci.plugins.workflow.support.pickles.serialization;

import org.jenkinsci.plugins.workflow.pickles.Pickle;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static java.util.Arrays.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class EphemeralPickleResolverTest extends Assert {
    @Test
    public void resolveNothing() throws Exception {
        ListenableFuture<?> f = new PickleResolver(new ArrayList<Pickle>()).rehydrate();
        assertSuccessfulCompletion(f);
    }

    @Test
    public void resolveSomething() throws Exception {
        TestPickle v1 = new TestPickle();
        TestPickle v2 = new TestPickle();
        ListenableFuture<?> f = new PickleResolver(asList(v1, v2)).rehydrate();

        assertInProgress(f);
        v1.f.set(null);
        assertInProgress(f);
        v2.f.set(null);
        assertSuccessfulCompletion(f);
    }

    /**
     * If a resolution of a value fails, the whole thing should fail.
     */
    @Test
    public void resolutionFails() throws Exception {
        TestPickle v1 = new TestPickle();
        TestPickle v2 = new TestPickle();
        ListenableFuture<?> f = new PickleResolver(asList(v1, v2)).rehydrate();

        assertInProgress(f);
        v1.f.set(null);
        assertInProgress(f);
        v2.f.setException(new NoSuchElementException());

        assertTrue(f.isDone());
        try {
            f.get();
            fail("Expected a failure");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof NoSuchElementException);
        }
    }

    private void assertInProgress(Future<?> f) {
        assertFalse(f.isDone());
        assertFalse(f.isCancelled());
    }

    private void assertSuccessfulCompletion(Future<?> f) throws Exception {
        assertTrue(f.isDone());
        f.get();
    }

    class TestPickle extends Pickle {
        SettableFuture<?> f = SettableFuture.create();
        @Override
        public ListenableFuture<?> rehydrate() {
            return f;
        }
    }
}
