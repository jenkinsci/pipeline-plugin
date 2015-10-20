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
import java.util.Map;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.MultiBranchProjectFactory;
import jenkins.branch.MultiBranchProjectFactoryDescriptor;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import static org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject.CRITERIA;
import org.kohsuke.stapler.DataBoundConstructor;

public class WorkflowMultiBranchProjectFactory extends MultiBranchProjectFactory.BySCMSourceCriteria {

    @DataBoundConstructor public WorkflowMultiBranchProjectFactory() {}

    @Override protected SCMSourceCriteria getSCMSourceCriteria(SCMSource source) {
        return CRITERIA;
    }

    @Override protected MultiBranchProject<?,?> doCreateProject(ItemGroup<?> parent, String name, Map<String,Object> attributes) {
        return new WorkflowMultiBranchProject(parent, name);
    }

    @Extension public static class DescriptorImpl extends MultiBranchProjectFactoryDescriptor {

        @Override public MultiBranchProjectFactory newInstance() {
            return new WorkflowMultiBranchProjectFactory();
        }

        @Override public String getDisplayName() {
            return "Workflow Jenkinsfile";
        }

    }

}
