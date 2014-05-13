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
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scaffolding to experiment with the call into {@link Step}.
 *
 * @author Kohsuke Kawaguchi
 */
public class DSL extends GroovyObjectSupport implements Serializable {
    private final FlowExecutionOwner handle;
    private transient CpsFlowExecution exec;

    public DSL(FlowExecutionOwner handle) {
        this.handle = handle;
    }

    protected Object readResolve() throws IOException {
        return this;
    }

    @Override
    public Object invokeMethod(String name, Object args) {
        try {
            if (exec==null)
                exec = (CpsFlowExecution) handle.get();
        } catch (IOException e) {
            throw new Error(e); // TODO
        }

        StepDescriptor d = StepDescriptor.getByFunctionName(name);
        if (d==null)
            throw new NoSuchMethodError("No such DSL method exists: "+name);

        CpsThread thread = CpsThread.current();

        AtomNode a = new AtomNodeImpl(exec, exec.iota(), true, thread.head.get());
        a.addAction(new LabelAction("Step: "+name));    // TODO: use CPS call stack to obtain the current call site source location

        NamedArgsAndClosure ps = parseArgs(args);
        Step s = d.newInstance(ps.namedArgs);

        CpsStepContext context = new CpsStepContext(thread,handle,a,ps.body);
        boolean sync;
        try {
            sync = s.start(context);
        } catch (Exception e) {
            context.onFailure(e);
            sync = true;
        }

        if (sync) {
            assert context.bodyInvoker==null : "If a step claims synchronous completion, it shouldn't invoke body";

            // if the execution has finished synchronously inside the start method
            // we just move on accordingly
            a.markAsCompleted();
            setNewHead(thread,a);
            return context.replay();
        } else {
            // let the world know that we have a new node that's running
            setNewHead(thread,a);

            // if it's in progress, suspend it until we get invoked later.
            // when it resumes, the CPS caller behaves as if this method returned with the resume value
            Continuable.suspend(context);

            // the above method throws an exception to unwind the call stack, and
            // the control then goes back to CpsFlowExecution.runNextChunk
            // so the execution will never reach here.
            throw new AssertionError();
        }
    }

    private void setNewHead(CpsThread thread, AtomNode a) {
        try {
            thread.head.setNewHead(a);
        } catch (IOException e) {
            // TODO: what's the proper way to report an exception here?
            throw new Error("Failed to persist new head: "+a,e);
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

            Map<String,Object> m = new HashMap<String,Object>(a.size());
            for (int i = 0; i < a.size(); i++) {
                m.put("arg"+i,a.get(i));
            }
            return new NamedArgsAndClosure(m,c);
        }

        return new NamedArgsAndClosure(Collections.singletonMap("arg1",arg),null);
    }

    private static final long serialVersionUID = 1L;
}
