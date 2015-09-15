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
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinitionDescriptor;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import jenkins.model.Jenkins;
import org.codehaus.groovy.control.CompilationFailedException;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.GroovyLanguage;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Kohsuke Kawaguchi
 */
@PersistIn(JOB)
public class CpsFlowDefinition extends FlowDefinition {
    private final String script;
    private final boolean sandbox;

    public CpsFlowDefinition(String script) {
        this(script, false);
    }

    @DataBoundConstructor
    public CpsFlowDefinition(String script, boolean sandbox) {
        StaplerRequest req = Stapler.getCurrentRequest();
        this.script = sandbox ? script : ScriptApproval.get().configuring(script, GroovyLanguage.get(), ApprovalContext.create().withCurrentUser().withItemAsKey(req != null ? req.findAncestorObject(Item.class) : null));
        this.sandbox = sandbox;
    }

    private Object readResolve() {
        if (!sandbox) {
            ScriptApproval.get().configuring(script, GroovyLanguage.get(), ApprovalContext.create());
        }
        return this;
    }

    public String getScript() {
        return script;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    // Used only from Groovy tests.
    public CpsFlowExecution create(FlowExecutionOwner handle, Action... actions) throws IOException {
        return create(handle, StreamTaskListener.fromStderr(), Arrays.asList(actions));
    }

    @Override
    @SuppressWarnings("deprecation")
    public CpsFlowExecution create(FlowExecutionOwner owner, TaskListener listener, List<? extends Action> actions) throws IOException {
        for (Action a : actions) {
            if (a instanceof CpsFlowFactoryAction) {
                CpsFlowFactoryAction fa = (CpsFlowFactoryAction) a;
                return fa.create(this,owner,actions);
            } else if (a instanceof CpsFlowFactoryAction2) {
                return ((CpsFlowFactoryAction2) a).create(this, owner, actions);
            }
        }
        return new CpsFlowExecution(sandbox ? script : ScriptApproval.get().using(script, GroovyLanguage.get()), sandbox, owner);
    }

    @Extension
    public static class DescriptorImpl extends FlowDefinitionDescriptor {

        @Inject public Snippetizer snippetizer;

        @Override
        public String getDisplayName() {
            return "Workflow script";
        }

        public FormValidation doCheckScript(@QueryParameter String value, @QueryParameter boolean sandbox) {
            Jenkins j = Jenkins.getInstance();
            if (j == null) {
                return FormValidation.ok();
            }
            try {
                new CpsGroovyShell(null).getClassLoader().parseClass(value);
            } catch (CompilationFailedException x) {
                return FormValidation.error(x.getLocalizedMessage());
            }
            return sandbox ? FormValidation.ok() : ScriptApproval.get().checking(value, GroovyLanguage.get());
        }

    }
}
