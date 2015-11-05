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
import hudson.model.JobPropertyDescriptor;
import hudson.security.ACL;
import hudson.security.Permission;
import javax.annotation.Nonnull;
import jenkins.branch.Branch;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.workflow.job.WorkflowJobProperty;

/**
 * Marker for jobs based on a specific branch.
 */
public class BranchJobProperty extends WorkflowJobProperty {

    private @Nonnull Branch branch;

    BranchJobProperty(@Nonnull Branch branch) {
        this.branch = branch;
    }

    public synchronized @Nonnull Branch getBranch() {
        return branch;
    }

    synchronized void setBranch(@Nonnull Branch branch) {
        branch.getClass();
        this.branch = branch;
    }

    @Override public ACL decorateACL(final ACL acl) {
        return new ACL() {
            @Override public boolean hasPermission(Authentication a, Permission permission) {
                // This project is managed by its parent and may not be directly configured or deleted.
                // Note that Item.EXTENDED_READ may still be granted, so you can still see Snippet Generator, etc.
                if (ACL.SYSTEM.equals(a)) {
                    return true; // e.g., DefaultDeadBranchStrategy.runDeadBranchCleanup
                } else if (permission == Item.CONFIGURE || permission == Item.DELETE) {
                    return false;
                } else {
                    return acl.hasPermission(a, permission);
                }
            }
        };
    }

    // TODO make WorkflowJob.isBuildable false if !branch.isBuildable to handle orphaned projects

    @Extension public static class DescriptorImpl extends JobPropertyDescriptor {

        @Override public String getDisplayName() {
            return "Based on branch";
        }

    }

}
