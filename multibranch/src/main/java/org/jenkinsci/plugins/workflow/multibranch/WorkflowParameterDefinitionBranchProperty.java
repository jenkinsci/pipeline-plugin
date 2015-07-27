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
import hudson.model.Job;
import hudson.model.Run;
import jenkins.branch.BranchPropertyDescriptor;
import jenkins.branch.MultiBranchProjectDescriptor;
import jenkins.branch.ParameterDefinitionBranchProperty;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.kohsuke.stapler.DataBoundConstructor;

public class WorkflowParameterDefinitionBranchProperty extends ParameterDefinitionBranchProperty {

    @DataBoundConstructor public WorkflowParameterDefinitionBranchProperty() {}

    @Override protected <P extends Job<P, B>, B extends Run<P, B>> boolean isApplicable(Class<P> clazz) {
        return clazz == WorkflowJob.class;
    }

    @Extension public static class DescriptorImpl extends BranchPropertyDescriptor {

        @Override protected boolean isApplicable(MultiBranchProjectDescriptor projectDescriptor) {
            return WorkflowMultiBranchProject.class.isAssignableFrom(projectDescriptor.getClazz());
        }

        @Override
        public String getDisplayName() {
            return "Parameters";
        }
    }

}
