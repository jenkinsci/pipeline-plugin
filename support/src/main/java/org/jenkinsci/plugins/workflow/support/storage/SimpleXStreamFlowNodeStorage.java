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

package org.jenkinsci.plugins.workflow.support.storage;

import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.actions.FlowNodeAction;
import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.core.JVM;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.Action;
import hudson.util.RobustReflectionConverter;
import hudson.util.XStream2;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * {@link FlowNodeStorage} that stores one node per one file.
 *
 * @author Kohsuke Kawaguchi
 */
public class SimpleXStreamFlowNodeStorage extends FlowNodeStorage {
    private final File dir;
    private final FlowExecution exec;

    private transient SoftReference<PersistenceContext> context;

    public SimpleXStreamFlowNodeStorage(FlowExecution exec, File dir) {
        this.exec = exec;
        this.dir = dir;
    }

    private PersistenceContext get() {
        PersistenceContext c=null;
        if (context!=null)
            c = context.get();
        if (c!=null)
            return c;

        context = new SoftReference<PersistenceContext>(c=new PersistenceContext());
        return c;
    }

    @Override
    public FlowNode getNode(String id) throws IOException {
        // TODO according to Javadoc this should return null if !getNodeFile(id).isFile()
        return get().loadOuter(id).node;
    }

    @Override
    public void storeNode(FlowNode n) throws IOException {
        get().store(n);
    }

    private XmlFile getNodeFile(String id) {
        return new XmlFile(XSTREAM, new File(dir,id+".xml"));
    }

    public List<Action> loadActions(FlowNode node) throws IOException {
        if (!getNodeFile(node.getId()).exists())
            return new ArrayList<Action>(); // not yet saved
        return get().loadOuter(node.getId()).actions();
    }

    /**
     * Just stores this one node
     */
    public void saveActions(FlowNode node, List<Action> actions) throws IOException {
        get().references.put(node.getId(),new Tag(node,actions));
        getNodeFile(node.getId()).write(new Tag(node,actions));
    }

    /**
     * {@link Converter} for FlowNodes so that we can persist references to other FlowNodes by their IDs.
     */
    private static class FlowNodeConverter implements Converter {
        private final RobustReflectionConverter ref;
        FlowNodeConverter(XStream2 owner) {
            ref = new RobustReflectionConverter(owner.getMapper(),new JVM().bestReflectionProvider());
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            FlowNode n = (FlowNode)source;

            if (context.get(ROOT) ==null) {
                context.put(ROOT,n);
                ref.marshal(n, writer, context);
            } else {
                // this is a reference to another FlowNode. Persist as ID

                PersistenceContext c = CONTEXT.get();
                if (c!=null) {
                    // if we are trying to track reference graphs, remember IDs that we've encountered
                    c.queue.add(n);
                }
                writer.setValue(n.getId());
            }
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            PersistenceContext c = CONTEXT.get();
            if (context.get(ROOT)==null) {
                context.put(ROOT,true);
                return ref.unmarshal(reader, context);
            } else {
                // reference to another FlowNode
                String id = reader.getValue();
                try {
                    return c.loadInner(id).node;
                } catch (IOException e) {
                    throw new XStreamException("Failed to read FlowNode:id="+id,e);
                }
            }
        }

        public boolean canConvert(Class type) {
            return FlowNode.class.isAssignableFrom(type);
        }

        private static final Object ROOT = "rootFlowNode";
    }

    /**
     * To group node and their actions together into one object.
     */
    private static class Tag {
        final /* @Nonnull except perhaps after deserialization */ FlowNode node;
        private final @CheckForNull Action[] actions;

        private Tag(@Nonnull FlowNode node, @Nonnull List<Action> actions) {
            this.node = node;
            this.actions = actions.isEmpty() ? null : actions.toArray(new Action[actions.size()]);
        }

        public @Nonnull List<Action> actions() {
            return actions != null ? Arrays.asList(actions) : Collections.<Action>emptyList();
        }
    }


    /**
     * If we see a reference to other {@link FlowNode}s while we are reading, we need to persist them as well.
     * Likewise, as we read nodes, we need to remember its ID/FlowNode mapping to fix up all the references.
     */
    private class PersistenceContext {
        // used while reading
        private final Map<String,Tag> references = new HashMap<String, Tag>();

        // used while writing
        private final List<FlowNode> queue = new LinkedList<FlowNode>();

        private void store(FlowNode n) throws IOException {
            PersistenceContext old = CONTEXT.get();
            CONTEXT.set(this);
            try {
                queue.add(n);
                while (!queue.isEmpty()) {
                    n = queue.remove(0);
                    XmlFile f = getNodeFile(n.getId());
                    if (!f.exists()) {
                        f.write(new Tag(n, Collections.<Action>emptyList()));
                    }
                }
            } finally {
                CONTEXT.set(old);
            }
        }

        private Tag loadOuter(String id) throws IOException {
            PersistenceContext old = CONTEXT.get();
            CONTEXT.set(this);
            try {
                return loadInner(id);
            } finally {
                CONTEXT.set(old);
            }
        }

        private Tag loadInner(String id) throws IOException {
            Tag v = references.get(id);
            if (v!=null)    return v;   // already loaded?

            // else load it now
            XmlFile nodeFile = getNodeFile(id);
            v = (Tag) nodeFile.read();
            if (v.node == null) {
                throw new IOException("failed to load flow node from " + nodeFile);
            }
            try {
                FlowNode$exec.set(v.node,exec);
            } catch (IllegalAccessException e) {
                throw (IllegalAccessError)new IllegalAccessError("Failed to set owner").initCause(e);
            }
            for (FlowNodeAction a : Util.filter(v.actions(), FlowNodeAction.class))
                a.onLoad(v.node);
            references.put(id,v);

            return v;
        }
    }

    private static ThreadLocal<PersistenceContext> CONTEXT = new ThreadLocal<PersistenceContext>();

    public static final XStream2 XSTREAM = new XStream2();

    private static final Field FlowNode$exec;

    static {
        XSTREAM.registerConverter(new FlowNodeConverter(XSTREAM));

        try {
            FlowNode$exec = FlowNode.class.getDeclaredField("exec");
            FlowNode$exec.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new Error(e);
        }
    }
}
