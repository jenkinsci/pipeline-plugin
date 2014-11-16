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

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import com.cloudbees.groovy.cps.impl.FunctionCallEnv;
import com.cloudbees.groovy.cps.impl.SourceLocation;
import com.cloudbees.groovy.cps.impl.TryBlockEnv;
import com.cloudbees.groovy.cps.sandbox.SandboxInvoker;
import com.google.common.util.concurrent.FutureCallback;
import hudson.model.Action;
import org.jenkinsci.plugins.workflow.actions.BodyInvocationAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.WorkflowBodyInvoker;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;

/**
 * Encapsulates how to evaluate the body closure of {@link CpsStepContext},
 * and schedules async evaluation of them.
 *
 * @author Kohsuke Kawaguchi
 */
@PersistIn(NONE)
public final class CpsBodyInvoker extends WorkflowBodyInvoker {
    private final List<Object> contextOverrides = new ArrayList<Object>();

    private final BodyReference body;

    private final CpsStepContext owner;

    private final List<Action> startNodeActions = new ArrayList<Action>();

    final CpsBodyExecution bodyExecution = new CpsBodyExecution();

    private String displayName;

    private boolean createBodyStartNode = true;

    private boolean started;

    CpsBodyInvoker(CpsStepContext owner, BodyReference body) {
        this.owner = owner;
        this.body = body;
    }

    @Override
    public CpsBodyInvoker withContext(Object override) {
        contextOverrides.add(override);
        return this;
    }

    @Override
    public CpsBodyInvoker withStartAction(Action a) {
        startNodeActions.add(a);
        return this;
    }

    @Override
    public CpsBodyInvoker withCallback(FutureCallback<Object> callback) {
        bodyExecution.addCallback(callback);
        return this;
    }

    @Override
    public CpsBodyInvoker withDisplayName(@Nullable String name) {
        this.displayName = name;
        createBodyStartNode = (name==null);
        return this;
    }

    /**
     * Schedules the execution.
     *
     * The actual launching of the body will be done later in {@link #launch} methods.
     */
    @Override
    public BodyExecution start() {
        if (owner.isSyncMode()) {
            // we process this in ThreadTaskImpl
            if (!owner.bodyInvokers.add(this))
                throw new IllegalStateException("Already started");
        } else {
            try {
                owner.getExecution().runInCpsVmThread(new FutureCallback<CpsThreadGroup>() {
                    @Override
                    public void onSuccess(CpsThreadGroup g) {
                        CpsThread thread = owner.getThread(g);
                        if (thread != null) {
                            launch(thread);
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        bodyExecution.onFailure(t);
                    }
                });
            } catch (IOException e) {
                bodyExecution.onFailure(e);
            }
        }

        return bodyExecution;
    }

    /**
     * Evaluates the body but grow the {@link FlowNode}s on the same head as the current thread.
     *
     * The net effect is as if the body evaluation happens in the same thread as in the caller thread.
     */
    @CpsVmThreadOnly
    /*package*/ void launch(CpsThread currentThread) {
        launch(currentThread, currentThread.head);
    }

    /**
     * Evaluates the body.
     *
     * If the body is a synchronous closure, this method evaluates the closure synchronously.
     * Otherwise, the body is asynchronous and the method schedules another thread to evaluate the body.
     *
     * In either case, the result of the evaluation is passed to {@link #bodyExecution}.
     *
     * @param currentThread
     *      The thread whose context the new thread will inherit.
     */
    @CpsVmThreadOnly
    /*package*/ void launch(CpsThread currentThread, FlowHead head) {

        StepStartNode sn = addBodyStartFlowNode(head);

        try {
            // TODO: handle arguments to closure
            Object x = body.getBody(currentThread).call();

            bodyExecution.onSuccess(x);   // body has completed synchronously
        } catch (CpsCallableInvocation e) {
            // execute this closure asynchronously
            // TODO: does it make sense that the new thread shares the same head?
            // this problem is captured as https://trello.com/c/v6Pbwqxj/70-allowing-steps-to-build-flownodes
            CpsThread t = currentThread.group.addThread(createContinuable(currentThread, e, sn), head,
                    ContextVariableSet.from(currentThread.getContextVariables(), contextOverrides));

            bodyExecution.startExecution(t);
        } catch (Throwable t) {
            // body has completed synchronously and abnormally
            bodyExecution.onFailure(t);
        }
    }

    /**
     * Inserts the flow node that indicates the beginning of the body invocation.
     *
     * @see Adapter#addBodyEndFlowNode()
     */
    private StepStartNode addBodyStartFlowNode(FlowHead head) {
        StepStartNode start = new StepStartNode(head.getExecution(),
                owner.getStepDescriptor(), head.get());
        start.addAction(new BodyInvocationAction());
        for (Action a : startNodeActions) {
            if (a!=null)
                start.addAction(a);
        }
        head.setNewHead(start);
        return start;
    }

    /**
     * Creates {@link Continuable} that executes the given invocation and pass its result to {@link FutureCallback}.
     *
     * The {@link Continuable} itself will just yield null. {@link CpsThreadGroup} considers the whole
     * execution a failure if any of the threads fail, so this behaviour ensures that a problem in the closure
     * body won't terminate the workflow.
     */
    private Continuable createContinuable(CpsThread currentThread, CpsCallableInvocation inv, BlockStartNode sn) {
        // we need FunctionCallEnv that acts as the back drop of try/catch block.
        // TODO: we need to capture the surrounding calling context to capture variables, and switch to ClosureCallEnv
        FunctionCallEnv caller = new FunctionCallEnv(null, new SuccessAdapter(bodyExecution,sn), null, null);
        if (currentThread.getExecution().isSandbox())
            caller.setInvoker(new SandboxInvoker());

        // catch an exception thrown from body and treat that as a failure
        TryBlockEnv env = new TryBlockEnv(caller, null);
        env.addHandler(Throwable.class, new FailureAdapter(bodyExecution,sn));

        return new Continuable(
            // this source location is a place holder for the step implementation.
            // perhaps at some point in the future we'll let the Step implementation control this.
            inv.invoke(env, SourceLocation.UNKNOWN, new SuccessAdapter(bodyExecution,sn)));
    }

    private static abstract class Adapter implements Continuation {
        final FutureCallback callback;
        final String startNodeId;

        public Adapter(FutureCallback callback, BlockStartNode startNode) {
            this.callback = callback;
            this.startNodeId = startNode.getId();
        }

        /**
         * Inserts the flow node that indicates the beginning of the body invocation.
         *
         * @see CpsBodyInvoker#addBodyStartFlowNode(FlowHead)
         */
        StepEndNode addBodyEndFlowNode() {
            try {
                FlowHead head = CpsThread.current().head;

                StepEndNode end = new StepEndNode(head.getExecution(),
                        (StepStartNode) head.getExecution().getNode(startNodeId),
                        head.get());
                end.addAction(new BodyInvocationAction());
                head.setNewHead(end);

                return end;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to grow the flow graph", e);
                throw new Error(e);
            }
        }

        private static final long serialVersionUID = 1L;
    }

    private static class FailureAdapter extends Adapter {
        private FailureAdapter(FutureCallback callback, BlockStartNode startNode) {
            super(callback, startNode);
        }

        @Override
        public Next receive(Object o) {
            addBodyEndFlowNode().addAction(new ErrorAction((Throwable)o));
            callback.onFailure((Throwable)o);
            return Next.terminate(null);
        }

        private static final long serialVersionUID = 1L;
    }

    private static class SuccessAdapter extends Adapter {
        private SuccessAdapter(FutureCallback callback, BlockStartNode startNode) {
            super(callback, startNode);
        }

        @Override
        public Next receive(Object o) {
            addBodyEndFlowNode();
            callback.onSuccess(o);
            return Next.terminate(null);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final Logger LOGGER = Logger.getLogger(CpsBodyInvoker.class.getName());
}
