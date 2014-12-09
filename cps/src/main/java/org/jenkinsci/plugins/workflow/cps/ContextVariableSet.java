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

import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;

import javax.annotation.concurrent.Immutable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.NONE;

/**
 * @author Kohsuke Kawaguchi
 */
@Immutable
@PersistIn(NONE)
final class ContextVariableSet implements Serializable {
    private final ContextVariableSet parent;
    private final List<Object> values = new ArrayList<Object>();

    ContextVariableSet(ContextVariableSet parent) {
        this.parent = parent;
    }

    <T> T get(Class<T> type) {
        for (ContextVariableSet s=this; s!=null; s=s.parent) {
            for (Object v : s.values) {
                if (type.isInstance(v))
                    return type.cast(v);
            }
        }
        return null;
    }

    /**
     * Obtains {@link ContextVariableSet} that inherits from the given parent and adds the specified overrides.
     */
    public static ContextVariableSet from(ContextVariableSet parent, List<Object> overrides) {
        if (overrides==null || overrides.isEmpty())
            return parent;  // nothing to override

        ContextVariableSet o = new ContextVariableSet(parent);
        o.values.addAll(overrides);
        return o;
    }

    private static final long serialVersionUID = 1L;
}
