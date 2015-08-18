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

import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.pickles.Pickle;
import org.jenkinsci.plugins.workflow.pickles.PickleFactory;
import hudson.ExtensionList;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.ObjectResolver;
import org.jboss.marshalling.river.RiverMarshallerFactory;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link ObjectOutputStream} compatible object graph serializer
 * that handles stateful objects for proper rehydration later.
 *
 * @author Kohsuke Kawaguchi
 * @see RiverMarshallerFactory
 * @see RiverReader
 */
public class RiverWriter implements Closeable {
    /**
     * File that we are writing to.
     */
    private final File file;

    /**
     * The location of the persisted file implies a {@link FlowExecutionOwner}, so we don't
     * actually store the owner object.
     */
    private final FlowExecutionOwner owner;

    /**
     * Writes to {@link #file}.
     */
    private final DataOutputStream dout;

    /**
     * Handles object graph -> byte[] conversion
     */
    private final Marshaller marshaller;

    private final int ephemeralsBackptr;

    private boolean pickling;

    /**
     * Persisted form of stateful objects that need special handling during rehydration.
     */
    List<Pickle> pickles = new ArrayList<Pickle>();

    // TODO: rename to HibernatingObjectOutputStream?
    public RiverWriter(File f, FlowExecutionOwner _owner) throws IOException {
        final ExtensionList<PickleFactory> pickleFactories = PickleFactory.all();
        if (pickleFactories.isEmpty()) {
            throw new IllegalStateException("JENKINS-26137: Jenkins is shutting down");
        }
        file = f;
        owner = _owner;
        dout = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        dout.writeLong(HEADER);
        dout.writeShort(VERSION);
        ephemeralsBackptr = dout.size();
        dout.writeInt(0);     // we'll back-fill this address with the pointer to the ephemerals stream

        MarshallingConfiguration config = new MarshallingConfiguration();
        //config.setSerializabilityChecker(new SerializabilityCheckerImpl());
        config.setObjectResolver(new ObjectResolver() {
            public Object readResolve(Object o) {
                throw new IllegalStateException();
            }

            public Object writeReplace(Object o) {
                if (o==owner)
                    return new DryOwner();

                if (pickling) {
                    for (PickleFactory f : pickleFactories) {
                        Pickle v = f.writeReplace(o);
                        if (v != null) {
                            pickles.add(v);
                            return new DryCapsule(pickles.size() - 1); // let Pickle be serialized into the stream
                        }
                    }
                }
                return o;
            }
        });

        marshaller = new RiverMarshallerFactory().createMarshaller(config);
        marshaller.start(Marshalling.createByteOutput(dout));
        pickling = true;
    }

    public void writeObject(Object o) throws IOException {
        marshaller.writeObject(o);
    }

    /**
     * For writing various typed objects and primitives.
     */
    public ObjectOutput getObjectOutput() {
        return marshaller;
    }

    public void close() throws IOException {
        marshaller.finish();
        int ephemeralsOffset = dout.size();

        // write the ephemerals stream
        pickling = false;
        marshaller.start(Marshalling.createByteOutput(dout));
        marshaller.writeObject(pickles);
        marshaller.finish();
        dout.close();

        // back fill the offset to the ephemerals stream
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        try {
            raf.seek(ephemeralsBackptr);
            raf.writeInt(ephemeralsOffset);
        } finally {
            raf.close();
        }
    }

    /*constant*/ static final long HEADER = 7330745437582215633L;
    /*constant*/ static final int VERSION = 1;
}
