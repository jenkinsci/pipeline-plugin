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

import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import java.io.IOException;
import java.util.List;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.MultiBranchProjectDescriptor;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * Representation of a set of workflows keyed off of source branches.
 */
@SuppressWarnings({"unchecked", "rawtypes"}) // core’s fault
public class WorkflowMultiBranchProject extends MultiBranchProject<WorkflowJob,WorkflowRun> {

    static final String SCRIPT = "jenkins.groovy";

    public WorkflowMultiBranchProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override protected BranchProjectFactory<WorkflowJob,WorkflowRun> newProjectFactory() {
        return new WorkflowBranchProjectFactory();
    }

    @Override public SCMSourceCriteria getSCMSourceCriteria(SCMSource source) {
        return new SCMSourceCriteria() {
            @Override public boolean isHead(SCMSourceCriteria.Probe probe, TaskListener listener) throws IOException {
                return probe.exists(SCRIPT);
            }
        };
    }

    // TODO unnecessary to override once branch-api on 1.592+
    @Override public Authentication getDefaultAuthentication(Queue.Item item) {
        return getDefaultAuthentication();
    }

    @Extension public static class DescriptorImpl extends MultiBranchProjectDescriptor {

        @Override public String getDisplayName() {
            return "Multibranch Workflow";
        }

        @Override public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new WorkflowMultiBranchProject(parent, name);
        }

        @Override public List<SCMDescriptor<?>> getSCMDescriptors() {
            // TODO this is a problem. We need to select only those compatible with Workflow.
            // Yet SCMDescriptor.isApplicable requires an actual Job, whereas we can only pass WorkflowJob.class!
            // Can we create a dummy WorkflowJob instance?
            return SCM.all();
        }

    }

}
