package org.jenkinsci.plugins.workflow.steps.build;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Vivek Pandey
 */
public class BuildTriggerStep extends AbstractStepImpl {

    private final String buildJobPath;

    private StepContext context;

    @StepContextParameter
    private transient TaskListener listener;

    @DataBoundConstructor
    public BuildTriggerStep(String value) {
        this.buildJobPath = value;
    }

    @Override
    protected boolean doStart(final StepContext context) throws Exception {
        this.context = context;

        listener.getLogger().println("Starting building project: "+buildJobPath);
        AbstractProject project = Jenkins.getInstance().getItem(buildJobPath,context.get(Job.class), AbstractProject.class);
        Jenkins.getInstance().getQueue().schedule(project, project.getQuietPeriod(), new BuildTriggerAction(context));
        return false;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        @Override
        public String getFunctionName() {
            return "build";
        }

        @Override
        public String getDisplayName() {
            return "Build a Job";
        }
    }
}
