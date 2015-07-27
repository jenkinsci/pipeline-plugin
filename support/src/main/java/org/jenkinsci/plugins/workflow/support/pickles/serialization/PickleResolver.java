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
import org.jenkinsci.plugins.workflow.support.concurrent.Futures;
import com.google.common.base.Function;
import com.google.common.util.concurrent.ListenableFuture;
import org.jboss.marshalling.ObjectResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ObjectResolver} that resolves {@link DryCapsule} to unpickled objects.
 *
 * @author Kohsuke Kawaguchi
 */
public class PickleResolver implements ObjectResolver {
    /**
     * Persisted forms of the stateful objects.
     */
    private final List<? extends Pickle> pickles;

    /**
     * Unpicked objects from {@link #pickles}, when they are all ready.
     */
    private List<Object> values;

    public PickleResolver(List<? extends Pickle> pickles) {
        this.pickles = pickles;
    }

    public Object get(int id) {
        return values.get(id);
    }

    public ListenableFuture<PickleResolver> rehydrate() {
        // if there's nothing to rehydrate, we are done
        if (pickles.isEmpty())
            return Futures.immediateFuture(this);

        List<ListenableFuture<?>> members = new ArrayList<ListenableFuture<?>>();
        for (Pickle r : pickles) {
            // TODO log("rehydrating " + r);
            ListenableFuture<?> future;
            try {
                future = r.rehydrate();
            } catch (RuntimeException x) {
                future = Futures.immediateFailedFuture(x);
            }
            members.add(Futures.transform(future, new Function<Object,Object>() {
                @Override public Object apply(Object input) {
                    // TODO log("rehydrated to " + input);
                    return input;
                }
            }));
        }

        ListenableFuture<List<Object>> all = Futures.allAsList(members);

        return Futures.transform(all,new Function<List<Object>, PickleResolver>() {
            public PickleResolver apply(List<Object> input) {
                values = input;
                return PickleResolver.this;
            }
        });
    }

    public Object readResolve(Object o) {
        if (o instanceof DryCapsule) {
            DryCapsule cap = (DryCapsule) o;
            return get(cap.id);
        }
        return o;
    }

    public Object writeReplace(Object original) {
        // only meant to be used for deserialization
        throw new IllegalStateException();
    }
}
