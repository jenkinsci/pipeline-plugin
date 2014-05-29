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

import groovy.lang.Closure;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;

import java.io.Serializable;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;

/**
 * Holder of {@link Closure} for {@link CpsStepContext}
 *
 * @author Kohsuke Kawaguchi
 * @see CpsStepContext#body
 */
@PersistIn(ANYWHERE)
abstract class BodyReference implements Serializable {
    protected final int id;

    protected BodyReference(int id) {
        this.id = id;
    }

    abstract Closure getBody(CpsThread caller);

    private static final long serialVersionUID = 1L;
}

class StaticBodyReference extends BodyReference {
    private final Closure body;

    public StaticBodyReference(int id, Closure body) {
        super(id);
        this.body = body;
    }

    @Override
    public Closure getBody(CpsThread caller) {
        return body;
    }

    private Object writeReplace() {
        // serializing as a handle
        return new HandleBodyReference(id);

        /*
            TODO: the following check is insufficient, because the pickling persistence
            also happens in the context of PROGRAM_STATE_SERIALIZATION.
         */

//        if (PROGRAM_STATE_SERIALIZATION.get()!=null) {
//            // serializing in CpsThreadGroup object graph
//            return this;
//        } else {
//        }
    }

    private static final long serialVersionUID = 1L;
}

class HandleBodyReference extends BodyReference {
    public HandleBodyReference(int id) {
        super(id);
    }

    @Override
    public Closure getBody(CpsThread caller) {
        return caller.group.closures.get(id);
    }

    private static final long serialVersionUID = 1L;
}
