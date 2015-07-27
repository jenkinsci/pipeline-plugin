/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import com.google.common.util.concurrent.ListenableFuture;
import hudson.model.Items;
import java.io.Serializable;
import org.jenkinsci.plugins.workflow.pickles.Pickle;
import org.jenkinsci.plugins.workflow.support.concurrent.Futures;

/**
 * A way of pickling Jenkins objects which have a well-defined XStream representation but are not {@link Serializable}.
 * Can also be used for objects which <em>are</em> {@link Serializable} but mistakenly so.
 * For any such type you wish to save, create a {@link SingleTypedPickleFactory} returning this pickle.
 * <p>Uses {@link Items#XSTREAM2} so suitable for things normally kept in job configuration.
 * <p>Note that the object ought to be self-contained and require no initialization,
 * so do not use this for anything with an {@code onLoad} or {@code setOwner} method, etc.
 */
public final class XStreamPickle extends Pickle {

    private final String xml;

    public XStreamPickle(Object o) {
        xml = Items.XSTREAM2.toXML(o);
    }

    @Override public ListenableFuture<?> rehydrate() {
        return Futures.immediateFuture(Items.XSTREAM2.fromXML(xml));
    }

}
