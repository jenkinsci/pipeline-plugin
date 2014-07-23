package org.jenkinsci.plugins.workflow.steps.build;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import javax.inject.Inject;

/**
 * @author Vivek Pandey
 */
public class BuildTriggerStepExecution extends StepExecution {
    @StepContextParameter
    private transient TaskListener listener;

    @Inject
    BuildTriggerStep step;

    @Override
    public boolean start() throws Exception {
        Jenkins jenkins = Jenkins.getInstance();

        listener.getLogger().println("Starting building project: "+step.buildJobPath);
        AbstractProject project = jenkins.getItem(step.buildJobPath,context.get(Job.class), AbstractProject.class);
        jenkins.getQueue().schedule(project, project.getQuietPeriod(), new BuildTriggerAction(context));
        return false;
    }

    @Override
    public void stop() {
        Jenkins jenkins = Jenkins.getInstance();

        Queue q = jenkins.getQueue();

        // if the build is still in the queue, abort it.
        // BuildTriggerListener will report the failure, so this method shouldn't call context.onFailure()
        for (Queue.Item i : q.getItems()) {
            BuildTriggerAction bta = i.getAction(BuildTriggerAction.class);
            if (bta!=null && bta.getStepContext().equals(context)) {
                q.cancel(i);
            }
        }

        // if there's any in-progress build already, abort that.
        // when the build is actually aborted, BuildTriggerListener will take notice and report the failure,
        // so this method shouldn't call context.onFailure()
        for (Computer c : jenkins.getComputers()) {
            for (Executor e : c.getExecutors()) {
                if (e.getCurrentExecutable() instanceof AbstractBuild) {
                    AbstractBuild b = (AbstractBuild) e.getCurrentExecutable();

                    BuildTriggerAction bta = b.getAction(BuildTriggerAction.class);
                    if (bta!=null && bta.getStepContext().equals(context)) {
                        e.interrupt();
                    }
                }
            }
        }
    }
}
