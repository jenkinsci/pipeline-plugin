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
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

/**
 * A step which uses some kind of {@link SCM}.
 */
abstract class SCMStep extends Step {

    private final boolean poll;
    private final boolean changelog;

    protected SCMStep(boolean poll, boolean changelog) {
        this.poll = poll;
        this.changelog = changelog;
    }

    public boolean isPoll() {
        return poll;
    }
    
    public boolean isChangelog() {
        return changelog;
    }

    protected abstract @Nonnull SCM createSCM();

    class StepExecutionImpl extends AbstractSynchronousStepExecution<Void> {
        @Override
        protected Void run() throws Exception {
            Run<?,?> run = context.get(Run.class);
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
            FilePath workspace = context.get(FilePath.class);
            TaskListener listener = context.get(TaskListener.class);
            Launcher launcher = context.get(Launcher.class);
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
                MultiSCMRevisionState state = run.getAction(MultiSCMRevisionState.class);
                if (state == null) {
                    state = new MultiSCMRevisionState();
                    run.addAction(state);
                }
                state.add(scm, pollingBaseline);
            }
            for (SCMListener l : SCMListener.all()) {
                l.onCheckout(run, scm, workspace, listener, changelogFile, pollingBaseline);
            }
            scm.postCheckout(run, launcher, workspace, listener);
            // TODO should we call buildEnvVars and return the result?
            return null;
        }
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new StepExecutionImpl();
    }

    protected static abstract class SCMStepDescriptor extends StepDescriptor {

        @Override public Set<Class<?>> getRequiredContext() {
            Set<Class<?>> s = new HashSet<Class<?>>();
            s.add(Run.class);
            s.add(Launcher.class);
            s.add(FilePath.class);
            s.add(TaskListener.class);
            return s;
        }

    }

}
