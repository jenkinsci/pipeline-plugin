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
import com.cloudbees.groovy.cps.Outcome;
import com.google.common.util.concurrent.FutureCallback;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyObjectSupport;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.jenkinsci.plugins.workflow.cps.ThreadTaskResult.*;
import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.PROGRAM;

/**
 * Scaffolding to experiment with the call into {@link Step}.
 *
 * @author Kohsuke Kawaguchi
 */
@PersistIn(PROGRAM)
public class DSL extends GroovyObjectSupport implements Serializable {
    private final FlowExecutionOwner handle;
    private transient CpsFlowExecution exec;

    public DSL(FlowExecutionOwner handle) {
        this.handle = handle;
    }

    protected Object readResolve() throws IOException {
        return this;
    }

    /**
     * Executes the {@link Step} implementation specified by the name argument.
     *
     * @return
     *      If the step completes execution synchronously, the result will be
     *      returned. Otherwise this method {@linkplain Continuable#suspend(Object) suspends}.
     */
    @Override
    public Object invokeMethod(String name, Object args) {
        try {
            if (exec==null)
                exec = (CpsFlowExecution) handle.get();
        } catch (IOException e) {
            throw new Error(e); // TODO
        }

        final StepDescriptor d = StepDescriptor.getByFunctionName(name);
        if (d==null)
            throw new NoSuchMethodError("No such DSL method exists: "+name);

        final NamedArgsAndClosure ps = parseArgs(args);

        CpsThread thread = CpsThread.current();

        FlowNode an;

        // TODO: generalize the notion of Step taking over the FlowNode creation.
        // see https://trello.com/c/v6Pbwqxj/13-allowing-steps-to-build-flownodes
        boolean hack = d instanceof ParallelStep.DescriptorImpl;

        if (ps.body == null && !hack) {
            an = new StepAtomNode(exec, d.getDisplayName(), thread.head.get());
            // TODO: use CPS call stack to obtain the current call site source location. See JENKINS-23013
            thread.head.setNewHead(an);
        } else {
            an = new StepStartNode(exec, d.getDisplayName(), thread.head.get());
            thread.head.setNewHead(an);
        }

        Step s = d.newInstance(ps.namedArgs);

        final CpsStepContext context = new CpsStepContext(d,thread,handle,an,ps.body);
        boolean sync;
        try {
            sync = s.start(context);
        } catch (Exception e) {
            context.onFailure(e);
            sync = true;
        }

        if (sync) {
            assert context.bodyInvokers.isEmpty() : "If a step claims synchronous completion, it shouldn't invoke body";

            if (context.getOutcome()==null) {
                context.onFailure(new AssertionError("Step "+s+" claimed to have ended synchronously, but didn't set the result via StepContext.onSuccess/onFailure"));
            }

            // if the execution has finished synchronously inside the start method
            // we just move on accordingly
            if (an instanceof StepStartNode) {
                // no body invoked, so EndNode follows StartNode immediately.
                thread.head.setNewHead(new StepEndNode(exec, (StepStartNode)an, an));
            }
            return context.replay();
        } else {
            // if it's in progress, suspend it until we get invoked later.
            // when it resumes, the CPS caller behaves as if this method returned with the resume value
            Continuable.suspend(new ThreadTaskImpl(context));

            // the above method throws an exception to unwind the call stack, and
            // the control then goes back to CpsFlowExecution.runNextChunk
            // so the execution will never reach here.
            throw new AssertionError();
        }
    }

    private static class NamedArgsAndClosure {
        final Map<String,Object> namedArgs;
        final Closure body;

        private NamedArgsAndClosure(Map<String,Object> namedArgs, Closure body) {
            this.namedArgs = namedArgs;
            this.body = body;
        }
    }

    /**
     * Given the Groovy style argument packing used in the sole object parameter of {@link GroovyObject#invokeMethod(String, Object)},
     * compute the named argument map and an optional closure that represents the body.
     *
     * <p>
     * Positional arguments are not allowed, unless it has a single argument, in which case
     * it is passed as an argument named "value", that is:
     *
     * <pre>
     * foo(x)  => foo(value:x)
     * </pre>
     *
     * <p>
     * This handling is designed after how Java defines literal syntax for {@link Annotation}.
     */
    private NamedArgsAndClosure parseArgs(Object arg) {
        if (arg instanceof Map)
            // TODO: convert the key to a string
            return new NamedArgsAndClosure((Map<String,Object>) arg, null);
        if (arg instanceof Closure)
            return new NamedArgsAndClosure(Collections.<String,Object>emptyMap(),(Closure)arg);

        if (arg instanceof Object[]) {// this is how Groovy appears to pack argument list into one Object for invokeMethod
            List a = Arrays.asList((Object[])arg);
            if (a.size()==0)
                return new NamedArgsAndClosure(Collections.<String,Object>emptyMap(),null);

            Closure c=null;

            Object last = a.get(a.size()-1);
            if (last instanceof Closure) {
                c = (Closure)last;
                a = a.subList(0,a.size()-1);
            }

            if (a.size()==1 && a.get(0) instanceof Map) {
                // this is how Groovy passes in Map
                return new NamedArgsAndClosure((Map)a.get(0),c);
            }

            switch (a.size()) {
            case 0:
                return new NamedArgsAndClosure(Collections.<String,Object>emptyMap(),c);
            case 1:
                return new NamedArgsAndClosure(Collections.singletonMap("value",a.get(0)),c);
            default:
                throw new IllegalArgumentException("Expected named arguments but got "+a);
            }
        }

        return new NamedArgsAndClosure(Collections.singletonMap("value",arg),null);
    }

    /**
     * If the step starts executing asynchronously, this task
     * executes at the safe point to switch {@link CpsStepContext} into the async mode.
     */
    private static class ThreadTaskImpl extends ThreadTask implements Serializable {
        private final CpsStepContext context;

        public ThreadTaskImpl(CpsStepContext context) {
            this.context = context;
        }

        @Override
        protected ThreadTaskResult eval(CpsThread cur) {
            invokeBody(cur);

            if (!context.switchToAsyncMode()) {
                // we have a result now, so just keep executing
                // TODO: if this fails with an exception, we need ability to resume by throwing an exception
                return resumeWith(context.getOutcome());
            } else {
                // beyond this point, StepContext can receive a result at any time and
                // that would result in a call to scheduleNextChunk(). So we the call to
                // switchToAsyncMode to happen inside 'synchronized(lock)', so that
                // the 'executing' variable gets set to null before the scheduleNextChunk call starts going.

                return suspendWith(new Outcome(context,null));
            }
        }

        private void invokeBody(CpsThread cur) {
            int idx=context.bodyInvokers.size();
            for (BodyInvoker b : context.bodyInvokers) {
                if (--idx==0) {
                    b.start(cur);
                } else {
                    FlowHead h = cur.head.fork();
                    b.start(cur, h, new HeadCollector(context,h));
                }
            }
            context.bodyInvokers.clear();
        }

        /**
         * When a new {@link CpsThread} that runs the body completes, record
         * its new head.
         */
        private static class HeadCollector implements FutureCallback, Serializable {
            private final CpsStepContext context;
            private final FlowHead head;

            public HeadCollector(CpsStepContext context, FlowHead head) {
                this.context = context;
                this.head = head;
            }

            private void onEnd() {
                head.getExecution().removeHead(head);
                context.bodyInvHeads.add(head.get().getId());
            }

            @Override
            public void onSuccess(Object result) {
                onEnd();
            }

            @Override
            public void onFailure(Throwable t) {
                onEnd();
            }
        }


        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
