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
import hudson.model.Item;
import java.io.IOException;
import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.BranchProjectFactoryDescriptor;
import jenkins.branch.MultiBranchProject;
import org.kohsuke.stapler.DataBoundConstructor;

public class WorkflowProjectFactoryImpl extends BranchProjectFactory<WorkflowBranchProject, WorkflowBranchBuild> {
    
    @DataBoundConstructor public WorkflowProjectFactoryImpl() {}

    @Override public WorkflowBranchProject newInstance(Branch branch) {
        return new WorkflowBranchProject((WorkflowMultiBranchProject) getOwner(), branch);
    }

    @Override public Branch getBranch(WorkflowBranchProject project) {
        return project.getBranch();
    }

    @Override public WorkflowBranchProject setBranch(WorkflowBranchProject project, Branch branch) {
        if (!project.getBranch().equals(branch)) {
            project.setBranch(branch);
            try {
                project.save();
            } catch (IOException e) {
                // TODO log
            }
        } else {
            project.setBranch(branch);
        }
        return project;
    }

    @Override public boolean isProject(Item item) {
        return item instanceof WorkflowBranchProject;
    }

    @Extension public static class DescriptorImpl extends BranchProjectFactoryDescriptor {

        @Override public String getDisplayName() {
            return "Fixed configuration";
        }

        @Override public boolean isApplicable(Class<? extends MultiBranchProject> clazz) {
            return WorkflowMultiBranchProject.class.isAssignableFrom(clazz);
        }

    }

}
