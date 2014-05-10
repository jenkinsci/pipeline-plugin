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
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.Outcome;
import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import com.cloudbees.groovy.cps.impl.FunctionCallEnv;
import com.cloudbees.groovy.cps.impl.SourceLocation;
import com.cloudbees.groovy.cps.impl.TryBlockEnv;
import org.jenkinsci.plugins.workflow.steps.Step;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;

import java.io.Serializable;
import java.util.List;

/**
 * Encapsulates how to evaluate the body closure of {@link CpsStepContext},
 * and schedules async evaluation of them.
 *
 * @author Kohsuke Kawaguchi
 */
final class BodyInvoker {
    /**
     * If {@link Step} requests an invocation of body, the target address is set here.
     */
    private final FutureCallback bodyCallback;

    /**
     * When {@link #bodyCallback} is set,
     */
    private final List<Object> contextOverrides;

    private final BodyReference body;

    BodyInvoker(BodyReference body, FutureCallback c, Object... contextOverrides) {
        this.body = body;

        if (!(c instanceof Serializable))
            throw new IllegalStateException("Callback must be persistable");
        this.bodyCallback = c;

        this.contextOverrides = ImmutableList.copyOf(contextOverrides);
    }

    /**
     * Evaluates the body.
     *
     * If the body is a synchronous closure, this method evaluates the closure synchronously.
     * Otherwise, the body is asynchronous and the method schedules another thread to evaluate the body.
     *
     * In either case, the result of the evaluation is passed to {@link #bodyCallback}.
     *
     * @param currentThread
     *      The thread whose context the new thread will inherit.
     */
    /*package*/ void start(CpsThread currentThread) {
        // capture variable, and prepare for another invocation
        final FutureCallback c = bodyCallback;
        final List<Object> co = contextOverrides;

        try {
            // TODO: handle arguments to closure
            Object x = body.getBody(currentThread).call();

            c.onSuccess(x);   // body has completed synchronously
        } catch (CpsCallableInvocation e) {
            // execute this closure asynchronously
            // TODO: does it make sense that the new thread shares the same head?
            // this problem is captured as https://trello.com/c/v6Pbwqxj/70-allowing-steps-to-build-flownodes
            CpsThread t = currentThread.group.addThread(createContinuable(e, c), currentThread.head,
                    ContextVariableSet.from(currentThread.getContextVariables(),co));
            t.resume(new Outcome(null, null));  // get the new thread going
        } catch (Throwable t) {
            // body has completed synchronously and abnormally
            c.onFailure(t);
        }
    }

    /**
     * Creates {@link Continuable} that executes the given invocation and pass its result to {@link FutureCallback}.
     *
     * The {@link Continuable} itself will just yield null. {@link CpsThreadGroup} considers the whole
     * execution a failure if any of the threads fail, so this behaviour ensures that a problem in the closure
     * body won't terminate the workflow.
     */
    private Continuable createContinuable(CpsCallableInvocation inv, final FutureCallback callback) {
        // we need FunctionCallEnv that acts as the back drop of try/catch block.
        // TODO: we need to capture the surrounding calling context to capture variables, and switch to ClosureCallEnv
        Env caller = new FunctionCallEnv(null, new SuccessAdapter(callback), null, null);

        // catch an exception thrown from body and treat that as a failure
        TryBlockEnv env = new TryBlockEnv(caller, null);
        env.addHandler(Throwable.class, new FailureAdapter(callback));

        return new Continuable(
            // this source location is a place holder for the step implementation.
            // perhaps at some point in the future we'll let the Step implementation control this.
            inv.invoke(env, SourceLocation.UNKNOWN, new SuccessAdapter(callback)));
    }

    private static class FailureAdapter implements Continuation {
        private final FutureCallback callback;

        public FailureAdapter(FutureCallback callback) {
            this.callback = callback;
        }

        @Override
        public Next receive(Object o) {
            callback.onFailure((Throwable)o);
            return Next.terminate(null);
        }

        private static final long serialVersionUID = 1L;
    }

    private static class SuccessAdapter implements Continuation {
        private final FutureCallback callback;

        public SuccessAdapter(FutureCallback callback) {
            this.callback = callback;
        }

        @Override
        public Next receive(Object o) {
            callback.onSuccess(o);
            return Next.terminate(null);
        }

        private static final long serialVersionUID = 1L;
    }
}
