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

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.Messages;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.scm.SCM;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.GitURIRequirementsBuilder;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;

/**
 * Runs Git using {@link GitSCM}.
 */
public final class GitStep extends SCMStep {

    private final String url;
    private String branch = "master";
    private String credentialsId;

    @DataBoundConstructor public GitStep(String url) {
        this.url = url;
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

    @DataBoundSetter public void setBranch(String branch) {
        this.branch = branch;
    }

    @DataBoundSetter public void setCredentialsId(String credentialsId) {
        this.credentialsId = Util.fixEmpty(credentialsId);
    }

    @Override public SCM createSCM() {
        return new GitSCM(createRepoList(url, credentialsId), Collections.singletonList(new BranchSpec("*/" + branch)), false, Collections.<SubmoduleConfig>emptyList(), null, null, null);
    }

    // copied from GitSCM
    static private List<UserRemoteConfig> createRepoList(String url, String credentialsId) {
        List<UserRemoteConfig> repoList = new ArrayList<UserRemoteConfig>();
        repoList.add(new UserRemoteConfig(url, null, null, credentialsId));
        return repoList;
    }


    private static StandardCredentials lookupCredentials(Item project, @Nonnull String credentialId, String uri) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StandardCredentials.class, project, ACL.SYSTEM,
                        GitURIRequirementsBuilder.fromUri(uri).build()),
                        CredentialsMatchers.withId(credentialId));
    }

    @Extension(optional=true) public static final class DescriptorImpl extends SCMStepDescriptor {

        public DescriptorImpl() {
            // Fail now if dependency plugin not loaded. Descriptor.<init> will actually fail anyway, but this is just to be sure.
            GitSCM.class.hashCode();
        }

        // copy/paste from GitSCM.DescriptorImpl

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project,
                                                     @QueryParameter String url) {
            if (project == null || !project.hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel();
            }
            return new StandardListBoxModel()
                    .withEmptySelection()
                    .withMatching(
                            GitClient.CREDENTIALS_MATCHER,
                            CredentialsProvider.lookupCredentials(StandardCredentials.class,
                                    project,
                                    ACL.SYSTEM,
                                    GitURIRequirementsBuilder.fromUri(url).build())
                    );
        }

        public FormValidation doCheckUrl(@AncestorInPath Item project,
                                         @QueryParameter String credentialsId,
                                         @QueryParameter String value) throws IOException, InterruptedException {

            if (project == null || !project.hasPermission(Item.CONFIGURE)) {
                return FormValidation.ok();
            }

            String url = Util.fixEmptyAndTrim(value);
            if (url == null)
                return FormValidation.error("Please enter repository url.");

            // get git executable on master
            final EnvVars environment = new EnvVars(EnvVars.masterEnvVars);

            GitClient git = Git.with(TaskListener.NULL, environment)
                    .using(GitTool.getDefaultInstallation().getGitExe())
                    .getClient();
            if (credentialsId != null) {
                StandardCredentials credentials = lookupCredentials(project, credentialsId, url);
                if (credentials != null) git.addDefaultCredentials(credentials);
            }

            // attempt to connect the provided URL
            try {
                git.getHeadRev(url, "HEAD");
            } catch (GitException e) {
                return FormValidation.error(Messages.UserRemoteConfig_FailedToConnect(e.getMessage()));
            }

            return FormValidation.ok();
        }

        @Override public String getFunctionName() {
            return "git";
        }

        @Override public String getDisplayName() {
            return "Git";
        }

    }

}
