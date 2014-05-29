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

import hudson.Extension;
import hudson.model.Action;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinitionDescriptor;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;

/**
 * @author Kohsuke Kawaguchi
 */
@PersistIn(JOB)
public class CpsFlowDefinition extends FlowDefinition {
    private final String script;

    @DataBoundConstructor
    public CpsFlowDefinition(String script) {
        this.script = script;
    }

    public String getScript() {
        return script;
    }

    // Used only from Groovy tests.
    public CpsFlowExecution create(FlowExecutionOwner handle, Action... actions) throws IOException {
        return create(handle, Arrays.asList(actions));
    }

    @Override
    public CpsFlowExecution create(FlowExecutionOwner owner, List<? extends Action> actions) throws IOException {
        for (Action a : actions) {
            if (a instanceof CpsFlowFactoryAction) {
                CpsFlowFactoryAction fa = (CpsFlowFactoryAction) a;
                return fa.create(this,owner,actions);
            }
        }
        return new CpsFlowExecution(getScript(),owner);
    }

    @Extension
    public static class DescriptorImpl extends FlowDefinitionDescriptor {
        @Override
        public String getDisplayName() {
            return "Groovy CPS DSL";
        }
    }
}
