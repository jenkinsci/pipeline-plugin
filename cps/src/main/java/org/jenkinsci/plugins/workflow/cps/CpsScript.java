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

import com.cloudbees.groovy.cps.SerializableScript;
import groovy.lang.Binding;
import hudson.EnvVars;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;

import java.io.IOException;
import java.io.PrintStream;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.PROGRAM;

/**
 * {@link SerializableScript} that overrides target of the output.
 *
 * @author Kohsuke Kawaguchi
 */
@PersistIn(PROGRAM)
public abstract class CpsScript extends SerializableScript {
    transient CpsFlowExecution execution;

    public CpsScript() {
    }

    public CpsScript(Binding binding) {
        super(binding);
    }

    @SuppressWarnings("deprecation") // TODO encoding of execution.owner.console?
    void loadEnvironment() throws IOException {
        Queue.Executable qe = execution.getOwner().getExecutable();
        if (qe instanceof Run) {
            PrintStream ps = execution.getOwner().getConsole();
            try {
                EnvVars env = ((Run) qe).getEnvironment(new StreamTaskListener(ps));
                getBinding().getVariables().putAll(env);
            } catch (InterruptedException x) {
                throw new IOException(x);
            }
        }
    }


    /**
     * We use DSL here to try invoke the step implementation, if there is Step implementation found it's handled or
     * it's an error
     */
    @Override
    public Object invokeMethod(String name, Object args) {
        //TODO: should it be using DSL tied to 'steps' property? well it doesn't matter. Let's create a new one for each
        // unknown function in case steps property is removed entirely
        DSL dsl = new DSL(execution.getOwner());
        return dsl.invokeMethod(name,args);
    }

    @Override
    public Object getProperty(String property) {
        if (property.equals("out")) {
            return execution.getOwner().getConsole();
        }
        return super.getProperty(property);
    }

    private Object readResolve() {
        execution = CpsFlowExecution.PROGRAM_STATE_SERIALIZATION.get();
        assert execution!=null;
        return this;
    }


    private static final long serialVersionUID = 1L;
}
