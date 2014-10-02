package org.jenkinsci.plugins.workflow.steps.build;

import hudson.AbortException;
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

    @Inject // used only during the start() method, so no need to be persisted
    transient BuildTriggerStep step;

    @Override
    public boolean start() throws Exception {
        Jenkins jenkins = Jenkins.getInstance();

        listener.getLogger().println("Starting building project: "+step.buildJobPath);
        AbstractProject project = jenkins.getItem(step.buildJobPath, getContext().get(Job.class), AbstractProject.class);
        if (project == null) {
            throw new AbortException("No parameterized job named " + step.buildJobPath + " found");
        }
        jenkins.getQueue().schedule(project, project.getQuietPeriod(), new BuildTriggerAction(getContext()));
        return false;
    }

    @Override
    public void stop() {
        Jenkins jenkins = Jenkins.getInstance();

        Queue q = jenkins.getQueue();

        // if the build is still in the queue, abort it.
        // BuildTriggerListener will report the failure, so this method shouldn't call getContext().onFailure()
        for (Queue.Item i : q.getItems()) {
            BuildTriggerAction bta = i.getAction(BuildTriggerAction.class);
            if (bta!=null && bta.getStepContext().equals(getContext())) {
                q.cancel(i);
            }
        }

        // if there's any in-progress build already, abort that.
        // when the build is actually aborted, BuildTriggerListener will take notice and report the failure,
        // so this method shouldn't call getContext().onFailure()
        for (Computer c : jenkins.getComputers()) {
            for (Executor e : c.getExecutors()) {
                if (e.getCurrentExecutable() instanceof AbstractBuild) {
                    AbstractBuild b = (AbstractBuild) e.getCurrentExecutable();

                    BuildTriggerAction bta = b.getAction(BuildTriggerAction.class);
                    if (bta!=null && bta.getStepContext().equals(getContext())) {
                        e.interrupt();
                    }
                }
            }
        }
    }
}
