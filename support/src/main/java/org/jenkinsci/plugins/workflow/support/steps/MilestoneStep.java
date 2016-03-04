package org.jenkinsci.plugins.workflow.support.steps;

import javax.annotation.CheckForNull;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;

/**
 * This step can be used to grant:
 * <ol>
 *   <li>Builds pass through the step in order (taking the build number as sorter field)</li>
 *   <li>Older builds will not proceed (they are aborted) if a newer one already entered the milestone</li>
 * </ol>
 *
 * When {@link #concurrency} is set the number of builds inside the milestone will be limited to that value and
 * the number of waiting builds is limited to 1, if a build reaches the milestone and an older build is waiting
 * to go through then the older one will be aborted.
 */
public class MilestoneStep extends AbstractStepImpl {

    @DataBoundSetter
    public @CheckForNull Integer concurrency;

    public Integer ordinal;

    @DataBoundConstructor 
    public MilestoneStep(Integer ordinal) {
        this.ordinal = ordinal;
    }

    @Extension public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(MilestoneStepExecution.class);
        }

        @Override public String getFunctionName() {
            return "milestone";
        }

        @Override public String getDisplayName() {
            return "Milestone";
        }

    }

}

