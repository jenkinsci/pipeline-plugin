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

import com.google.common.collect.ImmutableList;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.BallColor;
import hudson.model.Saveable;
import hudson.search.SearchItem;
import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static java.util.logging.Level.*;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * One node in a flow graph.
 */
@ExportedBean
public abstract class FlowNode extends Actionable implements Saveable {
    private final List<FlowNode> parents;

    private final String id;

    // this is a copy-on-write array so synchronization isn't needed between reader & writer.
    @edu.umd.cs.findbugs.annotations.SuppressWarnings("IS2_INCONSISTENT_SYNC")
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
     * <p>It will be false for a node which still has active children, like a step with a running body.
     * It will also be false for something that has finished but is pending child node creation,
     * such as a completed fork branch which is waiting for the join node to be created.
     * <p>This can only go from true to false and is a shortcut for {@link FlowExecution#isCurrentHead}.
     */
    @Exported
    public final boolean isRunning() {
        return getExecution().isCurrentHead(this);
    }

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

    @Restricted(DoNotUse.class)
    @Exported(name="parents")
    public List<String> getParentIds() {
        List<String> ids = new ArrayList<String>(2);
        for (FlowNode parent : getParents()) {
            ids.add(parent.getId());
        }
        return ids;
    }

    /**
     * Has to be unique within a {@link FlowExecution}.
     *
     * Needs to remain stable across serialization and JVM restarts.
     *
     * @see FlowExecution#getNode(String)
     */
    @Exported
    public String getId() {
        return id;
    }

    /**
     * Reference from the parent {@link SearchItem} is through {@link FlowExecution#getNode(String)}
     */
    public final String getSearchUrl() {
        return getId();
    }

    @Exported
    public String getDisplayName() {
        LabelAction a = getAction(LabelAction.class);
        if (a!=null)    return a.getDisplayName();
        else            return getTypeDisplayName();
    }

    public String getDisplayFunctionName() {
        String functionName = getTypeFunctionName();
        if (functionName == null) {
            return getDisplayName();
        } else {
            LabelAction a = getAction(LabelAction.class);
            if (a != null) {
                return functionName + ": " + a.getDisplayName();
            } else {
                return functionName;
            }
        }
    }

    /**
     * Returns colored orb that represents the current state of this node.
     *
     * TODO: this makes me wonder if we should support other colored states,
     * like unstable and aborted --- seems useful.
     */
    @Exported
    public BallColor getIconColor() {
        BallColor c = getError()!=null ? BallColor.RED : BallColor.BLUE;
        // TODO this should probably also be _anime in case this is a step node with a body and the body is still running (try FlowGraphTable for example)
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
     * Gets a human readable text that may include a {@link StepDescriptor#getFunctionName()}.
     * It would return "echo" for a flow node linked to an EchoStep or "ws {" for WorkspaceStep.
     *
     * For StepEndNode it would return "} // step.getFunctionName()".
     *
     * Note that this method should be abstract (supposed to be implemented in all subclasses), but keeping
     * it non-abstract to avoid binary incompatibilities.
     *
     * @return the text human-readable representation of the step function name
     *      or {@link FlowNode#getDisplayName()} by default (if not overriden in subclasses)
     */
    protected /* abstract */ String getTypeFunctionName() {
        return getDisplayName();
    }

    /**
     * Returns the URL of this {@link FlowNode}, relative to the context root of Jenkins.
     *
     * @return
     *      String like "job/foo/32/execution/node/abcde/" with no leading slash but trailing slash.
     */
    @Exported
    public String getUrl() throws IOException {
        return getExecution().getUrl()+"node/"+getId()+'/';
    }


    /**
     * SPI for subtypes to directly manipulate the actions field.
     *
     * When a brand new {@link FlowNode} is created, or when {@link FlowNode} and actions are
     * stored in close proximity, it is convenient to be able to set the {@link #actions}
     * so as to eliminate the separate call to {@link FlowActionStorage#loadActions(FlowNode)}.
     *
     * This method provides such an opportunity for subtypes.
     */
    protected void setActions(List<Action> actions) {
        this.actions = new CopyOnWriteArrayList<Action>(actions);
    }

/*
    We can't use Actionable#actions to store actions because they aren't transient,
    and we need to store actions elsewhere because this is the only mutable pat of FlowNode.

    So we create a separate transient field and store List of them there, and intercept every mutation.
 */
    @Exported
    @Override
    public synchronized List<Action> getActions() {
                if (actions==null) {
                    try {
                        actions = new CopyOnWriteArrayList<Action>(exec.loadActions(this));
                    } catch (IOException e) {
                        LOGGER.log(WARNING, "Failed to load actions for FlowNode id=" + id, e);
                        actions = new CopyOnWriteArrayList<Action>();
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
                    save();
                } catch (IOException e) {
                    LOGGER.log(WARNING, "failed to save actions for FlowNode id=" + id, e);
                }
            }
        };
    }

    /**
     * Explicitly save all the actions in this {@link FlowNode}.
     * Useful when an existing {@link Action} gets updated.
     */
    public void save() throws IOException {
        exec.saveActions(this, actions);
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

    @Override
    public String toString() {
        return getClass().getName() + "[id=" + id + "]";
    }

    private static final Logger LOGGER = Logger.getLogger(FlowNode.class.getName());
}
