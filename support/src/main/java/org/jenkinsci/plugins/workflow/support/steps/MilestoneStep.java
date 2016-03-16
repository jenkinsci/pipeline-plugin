package org.jenkinsci.plugins.workflow.support.steps;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.CheckForNull;
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

    /**
     * Optional milestone label.
     */
    private String label;

    @DataBoundConstructor
    public MilestoneStep() {
    }

    @DataBoundSetter
    public void setLabel(String label) {
        this.label = label;
    }

    @CheckForNull
    public String getLabel() {
        return label;
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

