package org.jenkinsci.plugins.workflow.steps.build;

import hudson.Extension;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.queue.QueueListener;

/**
 * @author Vivek Pandey
 */
@Extension
public class BuildQueueListener extends QueueListener {
    @Override
    public void onLeft(Queue.LeftItem li) {
        if(li.isCancelled()){
            Run run = (Run)li.getExecutable();
            BuildTriggerAction action = run.getAction(BuildTriggerAction.class);
            if(action != null) {
                action.getStepContext().onSuccess(run.getResult());
            }
        }
    }
}
