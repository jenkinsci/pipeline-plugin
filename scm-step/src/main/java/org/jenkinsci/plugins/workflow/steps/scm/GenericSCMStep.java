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

import hudson.Extension;
import hudson.model.Job;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import java.util.Collection;
import org.jenkinsci.plugins.workflow.util.StaplerReferer;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Runs a user-selected {@link SCM}.
 */
public final class GenericSCMStep extends SCMStep {

    private final SCM scm;

    @DataBoundConstructor public GenericSCMStep(SCM scm) {
        this.scm = scm;
    }

    public SCM getScm() {
        return scm;
    }

    @Override protected SCM createSCM() {
        return scm;
    }

    @Extension public static final class DescriptorImpl extends SCMStepDescriptor {

        @Override public String getFunctionName() {
            return "checkout";
        }

        @Override public String getDisplayName() {
            return "General SCM";
        }

        public Collection<? extends SCMDescriptor<?>> getApplicableDescriptors() {
            return SCM._for(StaplerReferer.findItemFromRequest(Job.class));
        }

    }

}
