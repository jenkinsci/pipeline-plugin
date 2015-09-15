/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package org.jenkinsci.plugins.workflow.steps.scm;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;

import java.io.File;
import java.io.Serializable;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * A step which uses some kind of {@link SCM}.
 */
public abstract class SCMStep extends AbstractStepImpl implements Serializable {

    private boolean poll = true;
    private boolean changelog = true;

    public boolean isPoll() {
        return poll;
    }
    
    @DataBoundSetter public void setPoll(boolean poll) {
        this.poll = poll;
    }
    
    public boolean isChangelog() {
        return changelog;
    }

    @DataBoundSetter public void setChangelog(boolean changelog) {
        this.changelog = changelog;
    }

    protected abstract @Nonnull SCM createSCM();

    public static final class StepExecutionImpl extends AbstractSynchronousNonBlockingStepExecution<Void> {

        @Inject private transient SCMStep step;
        @StepContextParameter private transient Run<?,?> run;
        @StepContextParameter private transient FilePath workspace;
        @StepContextParameter private transient TaskListener listener;
        @StepContextParameter private transient Launcher launcher;

        @Override
        protected Void run() throws Exception {
            step.checkout(run, workspace, listener, launcher);
            return null;
        }

        private static final long serialVersionUID = 1L;
    }

    public final void checkout(Run<?,?> run, FilePath workspace, TaskListener listener, Launcher launcher) throws Exception {
            File changelogFile = null;
            if (changelog) {
                for (int i = 0; ; i++) {
                    changelogFile = new File(run.getRootDir(), "changelog" + i + ".xml");
                    if (!changelogFile.exists()) {
                        break;
                    }
                }
            }
            SCM scm = createSCM();
            SCMRevisionState baseline = null;
            Run<?,?> prev = run.getPreviousBuild();
            if (prev != null) {
                MultiSCMRevisionState state = prev.getAction(MultiSCMRevisionState.class);
                if (state != null) {
                    baseline = state.get(scm);
                }
            }
            scm.checkout(run, launcher, workspace, listener, changelogFile, baseline);
            SCMRevisionState pollingBaseline = null;
            if (poll || changelog) {
                pollingBaseline = scm.calcRevisionsFromBuild(run, workspace, launcher, listener);
                if (pollingBaseline != null) {
                    MultiSCMRevisionState state = run.getAction(MultiSCMRevisionState.class);
                    if (state == null) {
                        state = new MultiSCMRevisionState();
                        run.addAction(state);
                    }
                    state.add(scm, pollingBaseline);
                }
            }
            for (SCMListener l : SCMListener.all()) {
                l.onCheckout(run, scm, workspace, listener, changelogFile, pollingBaseline);
            }
            scm.postCheckout(run, launcher, workspace, listener);
            // TODO should we call buildEnvVars and return the result?
    }

    public static abstract class SCMStepDescriptor extends AbstractStepDescriptorImpl {

        protected SCMStepDescriptor() {
            super(StepExecutionImpl.class);
        }

    }

    private static final long serialVersionUID = 1L;
}
