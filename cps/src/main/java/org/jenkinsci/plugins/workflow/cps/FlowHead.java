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

package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Outcome;
import hudson.model.Action;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.FlowNodeAction;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.PROGRAM;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;

/**
 * Growing tip of the node graph.
 *
 * <h2>Persistence</h2>
 * <p>
 * This object is a part of two independent serialization.
 * One is when this gets serialized as a part of {@link CpsFlowExecution} through XStream, for example
 * as a part of the workflow build object. We store the flow head ID and flow node ID in that case.
 *
 * <p>
 * The other is when this gets serialized as a part of the program state of {@link CpsThreadGroup},
 * through River. In this case we just store the flow head ID, and upon deserialization we resolve
 * back to one of the {@link CpsFlowExecution#heads} constant.
 *
 * @author Kohsuke Kawaguchi
 */
@PersistIn(PROGRAM)
final class FlowHead implements Serializable {
    private /*almost final except for serialization*/ int id;
    private /*almost final except for serialization*/ transient CpsFlowExecution execution;

    private FlowNode head; // TODO: rename to node

    FlowHead(CpsFlowExecution execution, int id) {
        this.id = id;
        this.execution = execution;
    }

    /**
     * Assigns a unique ID.
     */
    FlowHead(CpsFlowExecution execution) {
        this(execution, execution.iota());
    }

    /**
     * Creates a new {@link FlowHead} that points to the same node.
     */
    public FlowHead fork() {
        FlowHead h = new FlowHead(execution);
        h.head = this.head;
        execution.addHead(h);
        return h;
    }

    public int getId() {
        return id;
    }

    public CpsFlowExecution getExecution() {
        return execution;
    }

    void newStartNode(FlowStartNode n) throws IOException {
        for (Action a : execution.flowStartNodeActions) {
            if (a instanceof FlowNodeAction) {
                ((FlowNodeAction) a).onLoad(n);
            }
            n.addAction(a);
        }
        execution.flowStartNodeActions.clear();
        synchronized (execution) {
            this.head = execution.startNodes.push(n);
        }
        execution.storage.storeNode(head);
    }

    void setNewHead(FlowNode v) {
        try {
            this.head = v;
            execution.storage.storeNode(head);

            CpsThreadGroup c = CpsThreadGroup.current();
            if (c !=null) {
                // if the manipulation is from within the program executing thread, then
                // defer the notification till we get to a safe point.
                c.notifyNewHead(v);
            } else {
                // in recovering from error and such situation, we sometimes need to grow the graph
                // without running the program.
                execution.notifyListeners(v);
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Failed to record new head: " + v, e);
        }
    }

    FlowNode get() {
        return this.head;
    }

    /**
     * If the given outcome is a failure, mark the current head as a failure.
     */
    public void markIfFail(Outcome o) {
        // record the failure in a step
        if (o.isFailure()) {
            get().addAction(new ErrorAction(o.getAbnormal()));
        }
    }

    // used only during deserialization
    void setForDeserialize(FlowNode v) {
        this.head = v;
    }

    void remove() {
        getExecution().removeHead(this);
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.writeInt(id);
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        id = ois.readInt();
        // we'll replace this with one of execution.heads()
    }

    private Object readResolve() {
        execution = CpsFlowExecution.PROGRAM_STATE_SERIALIZATION.get();
        if (execution!=null)
            return execution.getFlowHead(id);
        else
            return this;
    }

    private static final Logger LOGGER = Logger.getLogger(FlowHead.class.getName());
    private static final long serialVersionUID = 1L;
}
