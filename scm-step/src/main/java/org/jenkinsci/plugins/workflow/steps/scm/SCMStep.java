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
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

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

    protected abstract @Nonnull SCM createSCM();

    @Override public boolean start(StepContext context) throws Exception {
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
        scm.checkout(run, launcher, workspace, listener, changelogFile);
        for (SCMListener l : SCMListener.all()) {
            SCMRevisionState pollingBaseline = null;
            if (poll) {
                if (!scm.supportsPolling()) {
                    throw new IllegalStateException(scm + " does not support polling");
                }
                pollingBaseline = scm.calcRevisionsFromBuild(run, workspace, launcher, listener);
            }
            l.onCheckout(run, scm, workspace, listener, changelogFile, pollingBaseline);
        }
        scm.postCheckout(run, launcher, workspace, listener);
        // TODO should we call buildEnvVars and return the result?
        context.onSuccess(null);
        return true;
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
