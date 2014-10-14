package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
import hudson.Util;
import hudson.tasks.BuildStep;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Artifact archiving.
 *
 * Could be a throw-away until {@link BuildStep} interop.
 *
 * @author Kohsuke Kawaguchi
 */
public class ArtifactArchiverStep extends AbstractStepImpl {

    private final String includes;
    private String excludes;
    // TBD: boolean fingerprint = true

    @DataBoundConstructor
    public ArtifactArchiverStep(String includes) {
        this.includes = includes;
    }

    public String getIncludes() {
        return includes;
    }

    public String getExcludes() {
        return excludes;
    }

    @DataBoundSetter public void setExcludes(String excludes) {
        this.excludes = Util.fixEmptyAndTrim(excludes);
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(ArtifactArchiverStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "archive";
        }

        @Override
        public String getDisplayName() {
            return "Archive Artifacts";
        }
    }
}
