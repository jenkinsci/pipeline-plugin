package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
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

    @DataBoundSetter
    String includes;

    @DataBoundSetter
    String excludes;

    /*
    @DataBoundSetter
    boolean fingerprint = true;
    */

    @DataBoundConstructor
    public ArtifactArchiverStep(String value) {
        this.includes = value;
    }

    public String getIncludes() {
        return includes;
    }

    public String getExcludes() {
        return excludes;
    }

    /*
    public boolean isFingerprint() {
        return fingerprint;
    }
    */

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
