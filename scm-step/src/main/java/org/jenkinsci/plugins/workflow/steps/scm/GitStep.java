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
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitTool;
import hudson.scm.SCM;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.util.Collection;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.GitURIRequirementsBuilder;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import jenkins.model.Jenkins;

/**
 * Runs Git using {@code GitSCM}.
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

    @SuppressWarnings("rawtypes")
    @Override public SCM createSCM() {
        try {
            ClassLoader cl = Jenkins.getActiveInstance().getPluginManager().uberClassLoader;
            return (SCM) cl.loadClass("hudson.plugins.git.GitSCM").getConstructor(
                List.class, // userRemoteConfigs
                List.class, // branches
                Boolean.class, // doGenerateSubmoduleConfigurations
                Collection.class, // submoduleCfg
                cl.loadClass("hudson.plugins.git.browser.GitRepositoryBrowser"), // browser
                String.class, // gitTool
                List.class // extensions
            ).newInstance(
                Collections.singletonList(cl.loadClass("hudson.plugins.git.UserRemoteConfig").getConstructor(String.class, String.class, String.class, String.class).newInstance(url, null, null, credentialsId)),
                Collections.singletonList(cl.loadClass("hudson.plugins.git.BranchSpec").getConstructor(String.class).newInstance("*/" + branch)),
                false,
                Collections.EMPTY_LIST,
                null,
                null,
                Collections.singletonList(cl.loadClass("hudson.plugins.git.extensions.impl.LocalBranch").getConstructor(String.class).newInstance(branch)));
        } catch (RuntimeException x) {
            throw x;
        } catch (Exception x) {
            throw new IllegalStateException(x);
        }
    }

    private static StandardCredentials lookupCredentials(Item project, @Nonnull String credentialId, String uri) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StandardCredentials.class, project, ACL.SYSTEM,
                        GitURIRequirementsBuilder.fromUri(uri).build()),
                        CredentialsMatchers.withId(credentialId));
    }

    @Extension(optional=true) public static final class DescriptorImpl extends SCMStepDescriptor {

        public DescriptorImpl() throws Exception {
            ClassLoader cl = Jenkins.getActiveInstance().getPluginManager().uberClassLoader;
            // Dependency plugin must be loaded…
            cl.loadClass("hudson.plugins.git.GitSCM");
            // …but not our own replacement.
            try {
                cl.loadClass("jenkins.plugins.git.GitStep");
                throw new IllegalStateException("skip the old copy of GitStep");
            } catch (ClassNotFoundException x) {
                // good
            }
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
                return FormValidation.error("Failed to connect to repository : " + e.getMessage());
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
