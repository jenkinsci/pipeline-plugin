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

package org.jenkinsci.plugins.workflow.graph;

import hudson.Functions;
import hudson.console.AnnotatedLargeText;
import hudson.model.BallColor;
import org.apache.commons.io.IOUtils;
import org.apache.commons.jelly.XMLOutput;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import com.google.common.collect.ImmutableList;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.search.SearchItem;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * @author Kohsuke Kawaguchi
 * @author Jesse Glick
 */
public abstract class FlowNode extends Actionable {
// there's no forward direction allow
//    List<FlowNode> next;

    private final List<FlowNode> parents;    // TODO: this will be getter

    private final String id;

    private transient CopyOnWriteArrayList<Action> actions;

    private transient final FlowExecution exec;

    protected FlowNode(FlowExecution exec, String id, List<FlowNode> parents) {
        this.id = id;
        this.exec = exec;
        this.parents = ImmutableList.copyOf(parents);
    }

    protected FlowNode(FlowExecution exec, String id, FlowNode... parents) {
        this.id = id;
        this.exec = exec;
        this.parents = ImmutableList.copyOf(parents);
    }

    /**
     * Transient flag that indicates if this node is currently actively executing something.
     * (As opposed to something that has finished but pending the child node creation, like
     * in when one of the fork branches has finished before the other and waiting for the join
     * node to be created.)
     *
     * This can only go from true to false. It can be only true for {@link FlowNode}s
     * that are returned from {@link FlowExecution#getCurrentHeads()}.
     */
    public abstract boolean isRunning();

    /**
     * If this node has terminated with an error, return an object that indicates that.
     * This is just a convenience method.
     */
    public final @CheckForNull ErrorAction getError() {
        return getAction(ErrorAction.class);
    }

    public @Nonnull FlowExecution getExecution() {
        return exec;
    }

    /**
     * Returns a read-only view of parents.
     */
    public List<FlowNode> getParents() {
        return parents;
    }

    /**
     * Has to be unique within a {@link FlowExecution}.
     *
     * Needs to remain stable across serialization and JVM restarts.
     *
     * @see FlowExecution#getNode(String)
     */
    public String getId() {
        return id;
    }

    /**
     * Reference from the parent {@link SearchItem} is through {@link FlowExecution#getNode(String)}
     */
    public final String getSearchUrl() {
        return getId();
    }

    public String getDisplayName() {
        LabelAction a = getAction(LabelAction.class);
        if (a!=null)    return a.getDisplayName();
        else            return getTypeDisplayName();
    }

    /**
     * Returns colored orb that represents the current state of this node.
     *
     * TODO: this makes me wonder if we should support other colored states,
     * like unstable and aborted --- seems useful.
     */
    public BallColor getIconColor() {
        BallColor c = getError()!=null ? BallColor.RED : BallColor.BLUE;
        if (isRunning())        c = c.anime();
        return c;
    }

    /**
     * Gets a human readable name for this type of the node.
     *
     * This is used to implement {@link #getDisplayName()} as a fallback in case {@link LabelAction} doesnt exist.
     */
    protected abstract String getTypeDisplayName();

    /**
     * Returns the URL of this {@link FlowNode}, relative to the context root of Jenkins.
     *
     * @return
     *      String like "/job/foo/32/execution/node/abcde" with leading slash but no trailing slash.
     */
    public String getUrl() {
        return getExecution().getUrl()+"/node/"+getId();
    }

/*
    We can't use Actionable#actions to store actions because they aren't transient,
    and we need to store actions elsewhere because this is the only mutable pat of FlowNode.

    So we create a separate transient field and store List of them there, and intercept every mutation.
 */
    @Override
    public List<Action> getActions() {
        if (actions==null) {
            synchronized (this) {
                if (actions==null) {
                    try {
                        actions = new CopyOnWriteArrayList<Action>(exec.loadActions(this));
                    } catch (IOException e) {
                        LOGGER.log(WARNING, "Failed to load actions for FlowNode id=" + id, e);
                        actions = new CopyOnWriteArrayList<Action>();
                    }
                }
            }
        }

        return new AbstractList<Action>() {
            @Override
            public Action get(int index) {
                return actions.get(index);
            }

            @Override
            public void add(int index, Action element) {
                actions.add(index, element);
                persist();
            }

            @Override
            public Action remove(int index) {
                Action old = actions.remove(index);
                persist();
                return old;
            }

            @Override
            public Action set(int index, Action element) {
                Action old = actions.set(index, element);
                persist();
                return old;
            }

            @Override
            public int size() {
                return actions.size();
            }

            private void persist() {
                try {
                    exec.saveActions(FlowNode.this, actions);
                } catch (IOException e) {
                    throw new IOError(e);
                }
            }
        };
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof FlowNode) {
            FlowNode that = (FlowNode) obj;
            return this.id.equals(that.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    private static final Logger LOGGER = Logger.getLogger(FlowNode.class.getName());
}
