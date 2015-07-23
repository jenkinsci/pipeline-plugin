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
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.BranchProjectFactoryDescriptor;
import jenkins.branch.MultiBranchProject;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.stapler.DataBoundConstructor;

public class WorkflowBranchProjectFactory extends BranchProjectFactory<WorkflowJob,WorkflowRun> {
    
    private static final Logger LOGGER = Logger.getLogger(WorkflowBranchProjectFactory.class.getName());

    @DataBoundConstructor public WorkflowBranchProjectFactory() {}

    @Override public WorkflowJob newInstance(Branch branch) {
        WorkflowJob job = new WorkflowJob((WorkflowMultiBranchProject) getOwner(), branch.getName());
        job.setDefinition(new SCMBinder());
        setBranch(job, branch);
        return job;
    }

    @Override public Branch getBranch(WorkflowJob project) {
        return project.getProperty(BranchJobProperty.class).getBranch();
    }

    @Override public WorkflowJob setBranch(WorkflowJob project, Branch branch) {
        BranchJobProperty property = project.getProperty(BranchJobProperty.class);
        try {
            if (property == null) {
                property = new BranchJobProperty();
                property.setBranch(branch);
                project.addProperty(property);
            } else if (!property.getBranch().equals(branch)) {
                property.setBranch(branch);
                project.save();
            }
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
        }
        return project;
    }

    @Override public boolean isProject(Item item) {
        return item instanceof WorkflowJob && ((WorkflowJob) item).getProperty(BranchJobProperty.class) != null;
    }

    @Extension public static class DescriptorImpl extends BranchProjectFactoryDescriptor {

        @Override public String getDisplayName() {
            return "Fixed configuration";
        }

        @SuppressWarnings("rawtypes") // TODO fix super class
        @Override public boolean isApplicable(Class<? extends MultiBranchProject> clazz) {
            return WorkflowMultiBranchProject.class.isAssignableFrom(clazz);
        }

    }

}
