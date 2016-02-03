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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.core.JVM;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.actions.FlowNodeAction;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.Action;
import hudson.util.RobustReflectionConverter;
import hudson.util.XStream2;
import org.jenkinsci.plugins.workflow.support.actions.NotExecutedNodeAction;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
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
    private final LoadingCache<String,FlowNode> nodeCache = CacheBuilder.newBuilder().softValues().build(new CacheLoader<String,FlowNode>() {
        @Override public FlowNode load(String key) throws Exception {
            return SimpleXStreamFlowNodeStorage.this.load(key).node;
        }
    });

    public SimpleXStreamFlowNodeStorage(FlowExecution exec, File dir) {
        this.exec = exec;
        this.dir = dir;
    }

    @Override
    public FlowNode getNode(String id) throws IOException {
        // TODO according to Javadoc this should return null if !getNodeFile(id).isFile()
        try {
            return nodeCache.get(id);
        } catch (ExecutionException x) {
            throw new IOException(x); // could unwrap if necessary
        }
    }

    @Override
    public void storeNode(FlowNode n) throws IOException {
        nodeCache.put(n.getId(), n);
        XmlFile f = getNodeFile(n.getId());
        if (!f.exists()) {
            f.write(new Tag(n, Collections.<Action>emptyList()));
        }
    }

    private XmlFile getNodeFile(String id) {
        return new XmlFile(XSTREAM, new File(dir,id+".xml"));
    }

    public List<Action> loadActions(FlowNode node) throws IOException {
        if (!getNodeFile(node.getId()).exists())
            return new ArrayList<Action>(); // not yet saved
        return load(node.getId()).actions();
    }

    /**
     * Just stores this one node
     */
    public void saveActions(FlowNode node, List<Action> actions) throws IOException {
        getNodeFile(node.getId()).write(new Tag(node,actions));
    }

    private Tag load(String id) throws IOException {
        XmlFile nodeFile = getNodeFile(id);
        Tag v = (Tag) nodeFile.read();
        if (v.node == null) {
            throw new IOException("failed to load flow node from " + nodeFile + ": " + nodeFile.asString());
        }
        try {
            FlowNode$exec.set(v.node, exec);
        } catch (IllegalAccessException e) {
            throw (IllegalAccessError) new IllegalAccessError("Failed to set owner").initCause(e);
        }
        for (FlowNodeAction a : Util.filter(v.actions(), FlowNodeAction.class)) {
            a.onLoad(v.node);
        }
        return v;
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

    public static final XStream2 XSTREAM = new XStream2();

    private static final Field FlowNode$exec;
    private static final Field FlowNode$parents;
    private static final Field FlowNode$parentIds;

    static {
        XSTREAM.addCompatibilityAlias("com.cloudbees.workflow.cps.checkpoint.NotExecutedNodeAction", NotExecutedNodeAction.class);
        XSTREAM.registerConverter(new Converter() {
            private final RobustReflectionConverter ref = new RobustReflectionConverter(XSTREAM.getMapper(), JVM.newReflectionProvider());
            // IdentityHashMap could leak memory. WeakHashMap compares by equals, which will fail with NPE in FlowNode.hashCode.
            private final Map<FlowNode,String> ids = CacheBuilder.newBuilder().weakKeys().<FlowNode,String>build().asMap();
            @Override public boolean canConvert(Class type) {
                return FlowNode.class.isAssignableFrom(type);
            }
            @Override public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
                ref.marshal(source, writer, context);
            }
            @Override public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
                try {
                    FlowNode n = (FlowNode) ref.unmarshal(reader, context);
                    ids.put(n, reader.getValue());
                    try {
                        @SuppressWarnings("unchecked") List<FlowNode> parents = (List<FlowNode>) FlowNode$parents.get(n);
                        if (parents != null) {
                            @SuppressWarnings("unchecked") List<String> parentIds = (List<String>) FlowNode$parentIds.get(n);
                            assert parentIds == null;
                            parentIds = new ArrayList<String>(parents.size());
                            for (FlowNode parent : parents) {
                                String id = ids.get(parent);
                                assert id != null;
                                parentIds.add(id);
                            }
                            FlowNode$parents.set(n, null);
                            FlowNode$parentIds.set(n, parentIds);
                        }
                    } catch (Exception x) {
                        assert false : x;
                    }
                    return n;
                } catch (RuntimeException x) {
                    x.printStackTrace();
                    throw x;
                }
            }
        });

        try {
            // TODO ugly, but we do not want public getters and setters for internal state.
            // Really FlowNode ought to have been an interface and the concrete implementations defined here, by the storage.
            FlowNode$exec = FlowNode.class.getDeclaredField("exec");
            FlowNode$exec.setAccessible(true);
            FlowNode$parents = FlowNode.class.getDeclaredField("parents");
            FlowNode$parents.setAccessible(true);
            FlowNode$parentIds = FlowNode.class.getDeclaredField("parentIds");
            FlowNode$parentIds.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new Error(e);
        }
    }
}
