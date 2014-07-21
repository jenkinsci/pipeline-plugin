package org.jenkinsci.plugins.workflow.support.steps;

import com.google.common.util.concurrent.FutureCallback;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.slaves.WorkspaceList;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import java.io.Serializable;

/**
 * @author Jesse Glick
 */
public class WorkspaceStepExecution extends StepExecution {
    public WorkspaceStepExecution(StepContext context) {
        super(context);
    }

    @Override
    public boolean start() throws Exception {
        Computer c = context.get(Computer.class);
        Run<?,?> r = context.get(Run.class);
        Job<?,?> j = r.getParent();
        if (!(j instanceof TopLevelItem)) {
            throw new Exception(j + " must be a top-level job");
        }
        Node n = c.getNode();
        if (n == null) {
            throw new Exception("computer does not correspond to a live node");
        }
        FilePath p = n.getWorkspaceFor((TopLevelItem) j);
        WorkspaceList.Lease lease = c.getWorkspaceList().allocate(p);
        FilePath workspace = lease.path;
        context.get(TaskListener.class).getLogger().println("Running in " + workspace);
        context.invokeBodyLater(new Callback(context, lease), workspace);
        return false;
    }

    private static final class Callback implements FutureCallback<Object>, Serializable {

        private final StepContext context;
        private final WorkspaceList.Lease lease;

        Callback(StepContext context, WorkspaceList.Lease lease) {
            this.context = context;
            this.lease = lease;
        }

        @Override public void onSuccess(Object result) {
            lease.release();
            context.onSuccess(result);
        }

        @Override public void onFailure(Throwable t) {
            lease.release();
            context.onFailure(t);
        }

    }
}
