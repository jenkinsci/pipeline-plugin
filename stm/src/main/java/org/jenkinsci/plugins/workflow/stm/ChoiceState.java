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

import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import hudson.Extension;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A state which lets the user run a little Groovy script to do some calculations and select the next state.
 */
public final class ChoiceState extends State {

    private final SecureGroovyScript script;

    @DataBoundConstructor public ChoiceState(String name, SecureGroovyScript script) {
        super(name);
        this.script = script.configuringWithNonKeyItem();
    }

    public SecureGroovyScript getScript() {
        return script;
    }

    @Override public FlowNode run(StepContext context, String nodeId, FlowExecution exec, FlowNode prior) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override public void success(STMExecution exec, String thread, Object returnValue) {
        throw new UnsupportedOperationException("TODO");
    }

    @Extension public static final class DescriptorImpl extends StateDescriptor {

        @Override public String getDisplayName() {
            return "Choice";
        }

    }

}
