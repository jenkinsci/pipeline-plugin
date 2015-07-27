/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.multibranch;

import com.thoughtworks.xstream.converters.Converter;
import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.ItemGroup;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import hudson.util.DescribableList;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import jenkins.branch.Branch;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinitionDescriptor;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.pickles.Pickle;
import org.jenkinsci.plugins.workflow.support.pickles.SingleTypedPickleFactory;
import org.jenkinsci.plugins.workflow.support.pickles.XStreamPickle;

/**
 * Adds an {@code scm} global variable to the script.
 * This makes it possible to run {@code checkout scm} to get your project sources in the right branch.
 */
class SCMBinder extends FlowDefinition {

    private static final Map<CpsFlowExecution,SCM> scms = Collections.synchronizedMap(new WeakHashMap<CpsFlowExecution,SCM>());

    @Override public FlowExecution create(FlowExecutionOwner handle, TaskListener listener, List<? extends Action> actions) throws Exception {
        Queue.Executable exec = handle.getExecutable();
        if (!(exec instanceof WorkflowRun)) {
            throw new IllegalStateException("inappropriate context");
        }
        WorkflowJob job = ((WorkflowRun) exec).getParent();
        BranchJobProperty property = job.getProperty(BranchJobProperty.class);
        if (property == null) {
            throw new IllegalStateException("inappropriate context");
        }
        Branch branch = property.getBranch();
        ItemGroup<?> parent = job.getParent();
        if (!(parent instanceof WorkflowMultiBranchProject)) {
            throw new IllegalStateException("inappropriate context");
        }
        SCMSource scmSource = ((WorkflowMultiBranchProject) parent).getSCMSource(branch.getSourceId());
        if (scmSource == null) {
            throw new IllegalStateException(branch.getSourceId() + " not found");
        }
        SCMHead head = branch.getHead();
        SCMRevision tip = scmSource.fetch(SCMHeadObserver.select(head), listener).result();
        if (tip == null) {
            throw new IllegalStateException("could not find branch tip on " + head);
        }
        SCM scm = scmSource.build(head, tip);
        // Fallback if one is needed: scm = branch.getScm()
        CpsFlowExecution execution = new CpsScmFlowDefinition(scm, WorkflowMultiBranchProject.SCRIPT).create(handle, listener, actions);
        scms.put(execution, scm); // stash for later
        return execution;
    }

    // Not registered as an @Extension, but in case someone calls it:
    @Override public FlowDefinitionDescriptor getDescriptor() {
        return new FlowDefinitionDescriptor() {
            @Override public String getDisplayName() {
                return "SCMBinder"; // should not be used in UI
            }
        };
    }

    // Note that this cannot be a GlobalVariable, since that must always be set.
    @Extension public static class Decorator extends GroovyShellDecorator {

        @Override public void configureShell(CpsFlowExecution context, GroovyShell shell) {
            if (context != null) {
                SCM scm = scms.remove(context);
                if (scm != null) {
                    shell.setVariable("scm", scm);
                }
            }
        }

    }

    /**
     * Ensures that {@code scm} is saved in its XML representation.
     * Necessary for {@code GitSCM} which is marked {@link Serializable}
     * yet includes a {@link DescribableList} which relies on a custom {@link Converter}.
     */
    @Extension public static class Pickler extends SingleTypedPickleFactory<SCM> {

        @Override protected Pickle pickle(SCM scm) {
            return new XStreamPickle(scm);
        }

    }

}
