/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package org.jenkinsci.plugins.workflow.support.actions;

import hudson.FilePath;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Item;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.labels.LabelAtom;
import hudson.security.AccessControlled;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.actions.FlowNodeAction;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.support.pickles.FilePathPickle;

public final class WorkspaceActionImpl extends WorkspaceAction implements FlowNodeAction {

    private static final long serialVersionUID = 1;
    
    private final String node;
    private final String path;
    private final Set<LabelAtom> labels;
    private transient FlowNode parent;

    public WorkspaceActionImpl(FilePath workspace, FlowNode parent) {
        // TODO see FilePathPickle:
        node = FilePathPickle.Listener.getChannelName(workspace.getChannel());
        if (node == null) {
            throw new IllegalStateException("no known slave for " + workspace + " among " + FilePathPickle.Listener.getChannelNames());
        }
        Jenkins j = Jenkins.getInstance();
        Node n = j == null ? null : node.isEmpty() ? j : j.getNode(node);
        labels = new TreeSet<LabelAtom>();
        if (n != null) {
            labels.addAll(n.getAssignedLabels());
            labels.remove(n.getSelfLabel());
        }
        path = workspace.getRemote();
        this.parent = parent;
    }

    @Override public String getNode() {
        return node;
    }

    @Override public String getPath() {
        return path;
    }

    @Override public Set<LabelAtom> getLabels() {
        return labels;
    }
    
    public FlowNode getParent() {
        return parent;
    }

    @Override public void onLoad(FlowNode parent) {
        this.parent = parent;
    }

    @Override public String getIconFileName() {
        return "folder.png";
    }

    @Override public String getDisplayName() {
        return "Workspace";
    }

    @Override public String getUrlName() {
        return "ws";
    }

    // Analogous to AbstractProject.doWs.
    // TODO this trick fails when a file or dir is named parent/node/path/workspace/iconFileName/displayName/urlName; how to convince Stapler that this method should take precedence?
    public DirectoryBrowserSupport doDynamic() throws IOException {
        Queue.Executable executable = parent.getExecution().getOwner().getExecutable();
        if (executable instanceof AccessControlled) {
            ((AccessControlled) executable).checkPermission(Item.WORKSPACE);
        }
        FilePath ws = getWorkspace();
        if (ws == null) {
            throw new FileNotFoundException();
        }
        return new DirectoryBrowserSupport(this, ws, "Workspace", "folder.png", true);
    }

}
