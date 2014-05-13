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

package org.jenkinsci.plugins.workflow.support.steps;

import com.google.common.util.concurrent.FutureCallback;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.slaves.WorkspaceList;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Allocates a workspace on the current node and uses that as the default directory for nested steps.
 */
public final class WorkspaceStep extends Step {

    @DataBoundConstructor public WorkspaceStep() {}

    @Override public boolean start(StepContext context) throws Exception {
        Computer c = context.get(Computer.class);
        Run<?,?> r = context.get(Run.class);
        Job<?,?> j = r.getParent();
        if (!(j instanceof TopLevelItem)) {
            throw new Exception(j + " must be a top-level job");
        }
        Node n = c.getNode();
        if (n == null) {
            throw new Exception("computer does not correspond to a live node");
        }
        FilePath p = n.getWorkspaceFor((TopLevelItem) j);
        WorkspaceList.Lease lease = c.getWorkspaceList().allocate(p);
        context.invokeBodyLater(new Callback(context, lease), lease.path);
        return false;
    }

    private static final class Callback implements FutureCallback<Object>, Serializable {

        private final StepContext context;
        private final WorkspaceList.Lease lease;

        Callback(StepContext context, WorkspaceList.Lease lease) {
            this.context = context;
            this.lease = lease;
        }

        @Override public void onSuccess(Object result) {
            lease.release();
            context.onSuccess(result);
        }

        @Override public void onFailure(Throwable t) {
            lease.release();
            context.onFailure(t);
        }

    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public Set<Class<?>> getRequiredContext() {
            Set<Class<?>> r = new HashSet<Class<?>>();
            r.add(Run.class);
            r.add(Computer.class);
            return r;
        }

        @Override public String getFunctionName() {
            return "ws";
        }

        @Override public Step newInstance(Map<String,Object> arguments) {
            return new WorkspaceStep();
        }

        @Override public String getDisplayName() {
            return "Allocate workspace";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

    }

}
