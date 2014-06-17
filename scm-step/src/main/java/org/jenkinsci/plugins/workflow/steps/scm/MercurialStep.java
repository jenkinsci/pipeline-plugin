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
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.SCM;
import java.util.Map;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Runs Git using {@link MercurialSCM}.
 */
public final class MercurialStep extends SCMStep {

    private final String url;
    private final String branch;
    private final String credentialsId;

    @DataBoundConstructor public MercurialStep(String url, String branch, String credentialsId, boolean poll, boolean changelog) {
        super(poll, changelog);
        this.url = url;
        this.branch = branch;
        this.credentialsId = credentialsId;
    }

    public String getUrl() {
        return url;
    }

    public String getBranch() {
        return branch;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @Override protected SCM createSCM() {
        return new MercurialSCM(null, url, MercurialSCM.RevisionType.BRANCH, branch, null, null, null, true, credentialsId, !isChangelog());
    }

    @Extension public static final class DescriptorImpl extends SCMStepDescriptor {

        @Override public String getFunctionName() {
            return "hg";
        }

        @Override public Step newInstance(Map<String,Object> arguments) {
            String branch = (String) arguments.get("branch");
            if (branch == null) {
                branch = "default";
            }
            return new MercurialStep((String) arguments.get("url"), branch, (String) arguments.get("credentialsId"), !Boolean.FALSE.equals(arguments.get("poll")), !Boolean.FALSE.equals(arguments.get("changelog")));
        }

        @Override public String getDisplayName() {
            return "Mercurial";
        }

    }

}
