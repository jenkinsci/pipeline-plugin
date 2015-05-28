package org.jenkinsci.plugins.workflow.support.steps.build;

import com.google.inject.Inject;
import hudson.AbortException;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

/**
 * @author Vivek Pandey
 */
public class BuildTriggerStepExecution extends AbstractStepExecutionImpl {
    @StepContextParameter
    private transient TaskListener listener;
    @StepContextParameter private transient Run<?,?> invokingRun;
    @StepContextParameter private transient FlowNode node;

    @Inject(optional=true) transient BuildTriggerStep step;

    @SuppressWarnings({"unchecked", "rawtypes"}) // cannot get from ParameterizedJob back to ParameterizedJobMixIn trivially
    @Override
    public boolean start() throws Exception {
        String job = step.getJob();
        listener.getLogger().println("Starting building project: " + job);
        final ParameterizedJobMixIn.ParameterizedJob project = Jenkins.getActiveInstance().getItem(job, invokingRun.getParent(), ParameterizedJobMixIn.ParameterizedJob.class);
        if (project == null) {
            throw new AbortException("No parameterized job named " + job + " found");
        }
        node.addAction(new LabelAction(Messages.BuildTriggerStepExecution_building_(project.getFullDisplayName())));
        List<Action> actions = new ArrayList<Action>();
        if (step.getWait()) {
            actions.add(new BuildTriggerAction(getContext(), step.isPropagate()));
        }
        actions.add(new CauseAction(new Cause.UpstreamCause(invokingRun)));
        List<ParameterValue> parameters = step.getParameters();
        if (parameters != null) {
            actions.add(new ParametersAction(parameters));
        }
        Integer quietPeriod = step.getQuietPeriod();
        if (quietPeriod == null) {
            quietPeriod = project.getQuietPeriod();
        }
        QueueTaskFuture<?> f = new ParameterizedJobMixIn() {
            @Override protected Job asJob() {
                return (Job) project;
            }
        }.scheduleBuild2(quietPeriod, actions.toArray(new Action[actions.size()]));
        if (f == null) {
            throw new AbortException("Failed to trigger build of " + project.getFullName());
        }
        if (step.getWait()) {
            return false;
        } else {
            getContext().onSuccess(null);
            return true;
        }
    }

    @Override
    public void stop(Throwable cause) {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return;
        }

        Queue q = jenkins.getQueue();

        // if the build is still in the queue, abort it.
        // BuildTriggerListener will report the failure, so this method shouldn't call getContext().onFailure()
        for (Queue.Item i : q.getItems()) {
            for (BuildTriggerAction bta : i.getActions(BuildTriggerAction.class)) {
                if (bta.getStepContext().equals(getContext())) {
                    // Note that it is a little questionable to cancel the queue item in case it has other causes,
                    // but in the common case that this is the only cause, it is most intuitive to do so.
                    // The same applies to aborting the actual build once started.
                    q.cancel(i);
                }
            }
        }

        // if there's any in-progress build already, abort that.
        // when the build is actually aborted, BuildTriggerListener will take notice and report the failure,
        // so this method shouldn't call getContext().onFailure()
        for (Computer c : jenkins.getComputers()) {
            for (Executor e : c.getExecutors()) {
                Queue.Executable exec = e.getCurrentExecutable();
                if (exec instanceof Run) {
                    Run<?,?> b = (Run) exec;
                    for (BuildTriggerAction bta : b.getActions(BuildTriggerAction.class)) {
                        if (bta.getStepContext().equals(getContext())) {
                            e.interrupt(Result.ABORTED, new BuildTriggerCancelledCause(cause));
                        }
                    }
                }
            }
        }
    }

    private static final long serialVersionUID = 1L;

}
