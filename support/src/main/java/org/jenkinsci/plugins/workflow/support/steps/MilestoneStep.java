package org.jenkinsci.plugins.workflow.support.steps;

import javax.annotation.CheckForNull;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;

/**
 * This step can be used to grant that:
 * <ol>
 *   <li>Builds pass through the step in order (taking the queued time as sorter field)</li>
 *   <li>Older builds will not proceed (aborted) if a newer one already passed the milestone step</li>
 * </ol>
 */
public class MilestoneStep {

    @DataBoundSetter
    public @CheckForNull Integer concurrency;

    @DataBoundSetter
    public @CheckForNull Integer ordinal;

    @DataBoundConstructor 
    public MilestoneStep() {
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

