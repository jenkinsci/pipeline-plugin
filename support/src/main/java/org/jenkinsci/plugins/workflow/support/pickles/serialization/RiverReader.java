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

import com.google.common.base.Function;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.commons.io.IOUtils;
import org.jboss.marshalling.ChainingObjectResolver;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.ObjectResolver;
import org.jboss.marshalling.SimpleClassResolver;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.marshalling.river.RiverMarshallerFactory;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.pickles.Pickle;
import org.jenkinsci.plugins.workflow.support.concurrent.Futures;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.apache.commons.io.IOUtils.*;

/**
 * Reads program data file that stores the object graph of the CPS-transformed program.
 *
 * <p>
 * The file consists of two separate object streams. The first stream contains a list of {@link Pickle}s,
 * which are used to restore stateful objects. The second stream is the main stream that contains
 * the main program state, which includes references to {@link DryCapsule} (which gets replaced to
 * their respective stateful objects.
 *
 * @author Kohsuke Kawaguchi
 */
public class RiverReader implements Closeable {
    private final File file;
    private final ClassLoader classLoader;
    /**
     * {@link DryOwner} in the serialized graph gets replaced by this object.
     */
    private final FlowExecutionOwner owner;

    /**
     * {@link ObjectResolver} that replaces {@link DryOwner} by the actual owner.
     */
    private ObjectResolver ownerResolver = new ObjectResolver() {
        @Override
        public Object readResolve(Object replacement) {
            if (replacement instanceof DryOwner)
                return owner;
            return replacement;
        }

        @Override
        public Object writeReplace(Object original) {
            throw new IllegalStateException();
        }
    };

    private InputStream in;

    public RiverReader(File f, ClassLoader classLoader, FlowExecutionOwner owner) throws IOException {
        this.file = f;
        this.classLoader = classLoader;
        this.owner = owner;
    }

    private int parseHeader(DataInputStream din) throws IOException {
        if (din.readLong()!= RiverWriter.HEADER)
            throw new IOException("Invalid stream header");

        short v = din.readShort();
        if (v!=1)
            throw new IOException("Unexpected stream version: "+v);

        return din.readInt();
    }

    /**
     * Step 1. Start unmarshalling pickles in the persisted stream,
     * and return the future that will signal when that is all complete.
     *
     * Once the pickles are restored, the future yields {@link Unmarshaller}
     * that can be then used to load the objects persisted by {@link RiverWriter}.
     */
    public ListenableFuture<Unmarshaller> restorePickles() throws IOException {
        in = openStreamAt(0);
        try {
        DataInputStream din = new DataInputStream(in);
        int offset = parseHeader(din);

        // load the pickle stream
        List<Pickle> pickles = readPickles(offset);
        final PickleResolver evr = new PickleResolver(pickles);

        // prepare the unmarshaller to load the main stream, by using yet-fulfilled PickleResolver
        MarshallingConfiguration config = new MarshallingConfiguration();
        config.setClassResolver(new SimpleClassResolver(classLoader));
        //config.setSerializabilityChecker(new SerializabilityCheckerImpl());
        config.setObjectResolver(combine(evr, ownerResolver));
        final Unmarshaller eu = new RiverMarshallerFactory().createUnmarshaller(config);
        eu.start(Marshalling.createByteInput(din));

        // start rehydrating, and when done make the unmarshaller available
        return Futures.transform(evr.rehydrate(), new Function<PickleResolver, Unmarshaller>() {
            public Unmarshaller apply(PickleResolver input) {
                return eu;
            }
        });
        } catch (IOException x) {
            in.close();
            throw x;
        }
    }

    private List<Pickle> readPickles(int offset) throws IOException {
        BufferedInputStream es = openStreamAt(offset);
        try {
            MarshallingConfiguration config = new MarshallingConfiguration();
            config.setClassResolver(new SimpleClassResolver(classLoader));
            config.setObjectResolver(ownerResolver);
            Unmarshaller eu = new RiverMarshallerFactory().createUnmarshaller(config);
            try {
                eu.start(Marshalling.createByteInput(es));
                return (List<Pickle>)eu.readObject();
            } catch (ClassNotFoundException e) {
                throw new IOException("Failed to read the stream",e);
            } finally {
                eu.finish();
            }
        } finally {
            closeQuietly(es);
        }
    }

    private BufferedInputStream openStreamAt(int offset) throws IOException {
        InputStream in = new FileInputStream(file);
        IOUtils.skipFully(in, offset);
        return new BufferedInputStream(in);
    }

    private ObjectResolver combine(ObjectResolver... resolvers) {
        return new ChainingObjectResolver(resolvers);
    }

    @Override public void close() {
        if (in != null) {
            try {
                in.close();
            } catch (IOException x) {
                Logger.getLogger(RiverReader.class.getName()).log(Level.WARNING, "could not close stream on " + file, x);
            }
        }
    }
}
