package org.jenkinsci.plugins.workflow.cps.global;

import hudson.Extension;
import hudson.model.RootAction;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.gitserver.FileBackedHttpGitRepository;

import java.io.File;

/**
 * Exposes the workflow libs as a git repository over HTTP.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class WorkflowLibRepository extends FileBackedHttpGitRepository implements RootAction {
    public WorkflowLibRepository() {
        super(new File(Jenkins.getInstance().root, "workflow-libs"));
    }

    @Override
    protected void checkPushPermission() {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "workflowLibs.git";
    }
}
