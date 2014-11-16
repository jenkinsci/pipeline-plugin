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
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.io.Serializable;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.support.actions.WorkspaceActionImpl;

/**
 * @author Jesse Glick
 */
public class WorkspaceStepExecution extends AbstractStepExecutionImpl {

    @StepContextParameter private transient Computer c;
    @StepContextParameter private transient Run<?,?> r;
    @StepContextParameter private transient TaskListener listener;
    @StepContextParameter private transient FlowNode flowNode;
    private BodyExecution body;

    @Override
    public boolean start() throws Exception {
        Job<?,?> j = r.getParent();
        if (!(j instanceof TopLevelItem)) {
            throw new Exception(j + " must be a top-level job");
        }
        Node n = c.getNode();
        if (n == null) {
            throw new Exception("computer does not correspond to a live node");
        }
        FilePath p = n.getWorkspaceFor((TopLevelItem) j);
        if (p == null) {
            throw new IllegalStateException(n + " is offline");
        }
        WorkspaceList.Lease lease = c.getWorkspaceList().allocate(p);
        FilePath workspace = lease.path;
        flowNode.addAction(new WorkspaceActionImpl(workspace, flowNode));
        listener.getLogger().println("Running in " + workspace);
        body = getContext().newBodyInvoker()
                .withContext(workspace)
                .withCallback(new Callback(getContext(), lease))
                .start();
        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        if (body!=null)
            body.cancel(cause);
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings("SE_BAD_FIELD") // lease is pickled
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

    private static final long serialVersionUID = 1L;

}
