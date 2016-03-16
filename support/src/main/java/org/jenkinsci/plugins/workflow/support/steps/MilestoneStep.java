package org.jenkinsci.plugins.workflow.support.steps;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

/**
 * This step can be used to grant:
 * <ol>
 *   <li>Builds pass through the step in order (taking the build number as sorter field)</li>
 *   <li>Older builds will not proceed (they are aborted) if a newer one already entered the milestone</li>
 *   <li>Once a build enters the milestone, it will be never aborted</li>
 *   <li>When a build passes a milestone, any older build that passed the previous milestone but not this one is aborted.</li>
 * </ol>
 */
public class MilestoneStep extends AbstractStepImpl {

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

