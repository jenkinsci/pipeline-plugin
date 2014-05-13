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
import groovy.lang.MissingMethodException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link SerializableScript} that overrides target of the output.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class CpsScript extends SerializableScript {
    transient CpsFlowExecution execution;

    public CpsScript() {
    }

    public CpsScript(Binding binding) {
        super(binding);
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
