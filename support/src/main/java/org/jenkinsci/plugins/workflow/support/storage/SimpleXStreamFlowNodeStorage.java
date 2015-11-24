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
import hudson.Util;
import hudson.XmlFile;
import hudson.model.Action;
import hudson.util.XStream2;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

    public SimpleXStreamFlowNodeStorage(FlowExecution exec, File dir) {
        this.exec = exec;
        this.dir = dir;
    }

    @Override
    public FlowNode getNode(String id) throws IOException {
        // TODO according to Javadoc this should return null if !getNodeFile(id).isFile()
        return load(id).node;
    }

    @Override
    public void storeNode(FlowNode n) throws IOException {
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
        // TODO try to migrate old storage like <parents><org.jenkinsci.plugins.workflow.graph.FlowStartNode>2</org.jenkinsci.plugins.workflow.graph.FlowStartNode></parents>
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

    static {
        try {
            // TODO just make a public setter for it already
            FlowNode$exec = FlowNode.class.getDeclaredField("exec");
            FlowNode$exec.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new Error(e);
        }
    }
}
