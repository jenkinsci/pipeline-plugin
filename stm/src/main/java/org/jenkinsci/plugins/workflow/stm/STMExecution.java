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

package org.jenkinsci.plugins.workflow.stm;

import com.google.common.util.concurrent.ListenableFuture;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.support.storage.FlowNodeStorage;
import org.jenkinsci.plugins.workflow.support.storage.SimpleXStreamFlowNodeStorage;
import org.jenkinsci.plugins.workflow.pickles.Pickle;
import org.jenkinsci.plugins.workflow.pickles.PickleFactory;
import hudson.model.Action;
import hudson.model.Result;
import hudson.security.ACL;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.CauseOfInterruption;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;

final class STMExecution extends FlowExecution {
    
    static final Logger LOGGER = Logger.getLogger(STMExecution.class.getName());

    /** Special state for end of execution. */
    static final String END = "end";

    /** Main thread ID. */
    static final String MAIN = "main";

    /** All available value factories; made a field for test injection. */
    static Collection<? extends PickleFactory> valueFactories;

    private final List<State> states;
    private final FlowExecutionOwner owner;
    private final FlowNodeStorage nodeStorage;

    @Override public Authentication getAuthentication() {
        return ACL.SYSTEM; // TODO
    }

    @Override public void interrupt(Result r, CauseOfInterruption... causes) throws IOException, InterruptedException {
        // TODO
    }

    static class Frame {
        String state;
        String id; // needed for blocks
        BodyExecutionCallback callback;
        @Override public String toString() {
            return "Frame[" + state + "," + id + "," + callback + "]";
        }
    }
    
    /** List of “program counters”, indexed by “thread name”, with the values being stacks of stack frames (where the top is the currently running block). */
    private final Map<String,Stack<Frame>> pcs = new LinkedHashMap<String,Stack<Frame>>();

    /** List of head node IDs, indexed by thread name. */
    private final Map<String,String> heads = new LinkedHashMap<String,String>();

    /** Generator of node IDs. */
    private int iota;

    private transient Map<String,State> statesByName;

    private transient List<GraphListener> listeners;

    STMExecution(List<State> states, FlowExecutionOwner owner, List<? extends Action> actions) throws IOException {
        this.states = states;
        this.owner = owner;
        nodeStorage = new SimpleXStreamFlowNodeStorage(this, owner.getRootDir());
        getStateMap();
        listeners = new CopyOnWriteArrayList<GraphListener>();
        // TODO what do we do with the initial actions?
    }

    synchronized Map<String,State> getStateMap() {
        if (statesByName == null) {
            statesByName = new HashMap<String,State>();
            for (State s : states) {
                State old = statesByName.put(s.getName(), s);
                if (old != null) {
                    throw new IllegalArgumentException(">1 state named " + s.getName());
                }
            }
        }
        return statesByName;
    }

    private synchronized String newID() {
        return String.valueOf(iota++);
    }

    /**
     * Called from a state to ask the engine to push the program counter forward and start the next step.
     * @param thread the thread name
     * @param next the next state to run
     */
    void next(String thread, String next) {
        Stack<Frame> stack = pcs.get(thread);
        if (stack == null) {
            LOGGER.log(Level.WARNING, "illegal attempt to continue finished thread {0}", thread);
            return;
        }
        LOGGER.log(Level.FINE, "next state is {0} on {1} with stack {2}", new Object[] {next, thread, stack});
        String priorID = heads.get(thread);
        assert priorID != null;
        FlowNode prior;
        try {
            prior = getNode(priorID);
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
            return;
        }
        if (next.equals(END)) {
            stack.pop();
            if (stack.isEmpty()) {
                LOGGER.log(Level.FINE, "finishing thread {0}", thread);
                pcs.remove(thread);
                heads.remove(thread);
                try {
                    addingHead(new BlockEndNode<BlockStartNode>(this, newID(), /*TODO*/null, prior) {
                        @Override protected String getTypeDisplayName() {
                            return "Thread end";
                        }
                    });
                    // TODO keep a list of all thread end nodes to collect into finals list in finish()
                    if (heads.isEmpty()) {
                        finish(Result.SUCCESS);
                    }
                } catch (Exception x) {
                    // What to do about it?
                    LOGGER.log(Level.WARNING, null, x);
                }
            } else {
                Frame caller = stack.peek();
                LOGGER.log(Level.FINE, "finishing subroutine from {0} on {1}", new Object[] {caller, thread});
                State s = getStateMap().get(caller.state);
                assert s instanceof BlockState : "found " + s + " rather than a BlockState on the stack for " + caller.state;
                BodyExecutionCallback callback = caller.callback;
                assert callback != null : "no callback defined for " + caller.state;
                caller.callback = null;
                STMContext context = null; // TODO
                callback.onSuccess(context, null); // TODO should there be a way of passing a return value from the block?
                heads.put(thread, caller.id);
                try {
                    addingHead(new BlockEndNode<BlockStartNode>(this, newID(), /*TODO*/null, /* TODO is this right? or should it be from caller.id? */prior) {
                        @Override protected String getTypeDisplayName() {
                            return "Block end";
                        }
                    });
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, null, x);
                }
            }
        } else {
            State s = getStateMap().get(next);
            if (s == null) {
                LOGGER.log(Level.WARNING, "no such state {0}", next);
                // How better to recover?
                next(thread, END);
                return;
            }
            String id = newID();
            FlowNode n = s.run(new STMContext(owner, id, next, thread), id, this, prior);
            stack.peek().state = next;
            stack.peek().id = id;
            heads.put(thread, id);
            try {
                addingHead(n);
            } catch (IOException x) {
                // What to do about it?
                LOGGER.log(Level.WARNING, null, x);
            }
        }
    }

    void beginBlock(String thread, BodyExecutionCallback callback) {
        Stack<Frame> stack = pcs.get(thread);
        if (stack == null) {
            LOGGER.log(Level.WARNING, "illegal attempt to continue finished thread {0}", thread);
            return;
        }
        LOGGER.log(Level.FINE, "begin block on {0} from {1}", new Object[] {thread, stack});
        stack.peek().callback = callback;
        stack.push(new Frame());
    }

    private void addingHead(FlowNode node) throws IOException {
        LOGGER.log(Level.FINE, "adding head: {0}", node/* NPE when Jenkins.instance == null: .getDisplayName()*/);
        nodeStorage.storeNode(node);
        for (GraphListener listener : listeners) {
            listener.onNewHead(node);
        }
    }

    @Override public void start() throws IOException {
        FlowStartNode start = new FlowStartNode(this, newID());
        heads.put(MAIN, start.getId());
        addingHead(start);
        // TODO factor this out into a general thread start function:
        BlockStartNode blockStart = new BlockStartNode(this, newID(), start) {
            @Override protected String getTypeDisplayName() {
                return "Thread start";
            }
        };
        heads.put(MAIN, blockStart.getId());
        addingHead(blockStart);
        LOGGER.fine("starting flow");
        if (states.isEmpty())  {
            // TODO is there a cleaner way of handling this?
            try {
                finish(Result.SUCCESS);
                return;
            } catch (InterruptedException x) {
                throw new IOException(x);
            }
        }
        Stack<Frame> stack = new Stack<Frame>();
        stack.push(new Frame());
        pcs.put(MAIN, stack);
        next(MAIN, states.get(0).getName());
    }
    
    void success(String id, Object returnValue) {
        for (Map.Entry<String,String> entry : heads.entrySet()) { // TODO synchronization
            if (id.equals(entry.getValue())) {
                String thread = entry.getKey();
                Stack<Frame> stack = pcs.get(thread);
                assert stack != null;
                String stateName = stack.peek().state;
                assert stateName != null;
                State s = getStateMap().get(stateName);
                assert s != null : "no such state " + stateName;
                LOGGER.log(Level.FINE, "success from state {0} returning {1}", new Object[] {stateName, returnValue});
                s.success(this, thread, returnValue);
                return;
            }
        }
        LOGGER.log(Level.WARNING, "{0} is not being run on any current thread", id);
    }

    @Override public void onLoad() {
        listeners = new CopyOnWriteArrayList<GraphListener>();
        // TODO
    }

    @Override public List<FlowNode> getCurrentHeads() {
        List<FlowNode> r = new ArrayList<FlowNode>();
        for (String head : heads.values()) {
            try {
                FlowNode node = getNode(head);
                if (node == null) {
                    LOGGER.log(Level.WARNING, "no such head node {0}", head);
                    continue;
                }
                r.add(node);
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, null, x);
            }
        }
        return r;
    }

    @Override
    public boolean isCurrentHead(FlowNode n) {
        return heads.values().contains(n.getId());
    }

    @Override
    public ListenableFuture<List<StepExecution>> getCurrentExecutions() {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override public void addListener(GraphListener listener) {
        listeners.add(listener);
    }
    
    private void finish(Result r) throws IOException, InterruptedException {
        LOGGER.log(Level.FINE, "finishing with {0}", r);
        List<FlowNode> finals = new ArrayList<FlowNode>();
        for (Map.Entry<String,String> entry : heads.entrySet()) {
            String thread = entry.getKey();
            String priorID = entry.getValue();
            assert priorID != null;
            FlowNode prior = getNode(priorID);
            BlockEndNode<BlockStartNode> blockEndNode = new BlockEndNode<BlockStartNode>(this, newID(), /*TODO*/ null, prior) {
                @Override protected String getTypeDisplayName() {
                    return "Thread end";
                }
            };
            addingHead(blockEndNode);
            finals.add(blockEndNode);
        }
        pcs.clear();
        heads.clear();
        String endID = newID();
        heads.put(MAIN, endID);
        addingHead(new FlowEndNode(this, endID, /*TODO*/null, r, finals.toArray(new FlowNode[finals.size()])));
    }

    private static Object perhapsConvertToValue(Object object) {
        // TODO this will not work, since various serializable objects (such as WorkspaceStep.Callback) hold references to objects that should be pickled
        for (PickleFactory f : (valueFactories == null ? PickleFactory.all() : valueFactories)) {
            Pickle v = f.writeReplace(object);
            if (v != null) {
                return v;
            }
        }
        return object;
    }

    @Override public FlowExecutionOwner getOwner() {
        return owner;
    }

    @Override public FlowNode getNode(String id) throws IOException {
        return nodeStorage.getNode(id);
    }

    public List<Action> loadActions(FlowNode node) throws IOException {
        return nodeStorage.loadActions(node);
    }

    public void saveActions(FlowNode node, List<Action> actions) throws IOException {
        nodeStorage.saveActions(node, actions);
    }

}
