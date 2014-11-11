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

package org.jenkinsci.plugins.workflow.actions;

import hudson.FilePath;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.remoting.VirtualChannel;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;

/**
 * Represents the fact that a step run on a particular workspace.
 */
public abstract class WorkspaceAction implements Action {

    /** The {@link Node#getNodeName} of the workspace. */
    public abstract @Nonnull String getNode();

    /** The {@link FilePath#getRemote} of the workspace. */
    public abstract @Nonnull String getPath();

    /**
     * The {@link Node#getAssignedLabels} of the node owning the workspace.
     * {@link Node#getSelfLabel} should be exempted, so this set may be empty in the typical case.
     * (Could be reconstructed in most cases via {@link Jenkins#getNode} on {@link #getNode},
     * but not for a slave which has since been removed, common with clouds.)
     */
    public abstract @Nonnull Set<LabelAtom> getLabels();

    /** Reconstructs the live workspace, if possible. */
    public final @CheckForNull FilePath getWorkspace() {
        // TODO copied from FilePathPickle. WorkspaceListLeasePickle also needs to do the same. Perhaps we need to extract a FilePathHandle or similar?
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            return null;
        }
        Computer c = j.getComputer(getNode());
        if (c == null) {
            return null;
        }
        VirtualChannel ch = c.getChannel();
        if (ch == null) {
            return null;
        }
        return new FilePath(ch, getPath());
    }

}
