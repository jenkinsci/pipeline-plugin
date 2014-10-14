package org.jenkinsci.plugins.workflow.cps.global;

import hudson.Extension;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;
import org.jenkinsci.plugins.gitserver.RepositoryResolver;

import javax.inject.Inject;
import java.io.IOException;

/**
 * Exposes the workflow libs as a git repository over SSH.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class WorkflowLibSshRepository extends RepositoryResolver {
    @Inject
    WorkflowLibRepository repo;

    @Override
    public ReceivePack createReceivePack(String fullRepositoryName) throws IOException, InterruptedException {
        if (isMine(fullRepositoryName))
            return repo.createReceivePack(repo.openRepository());
        return null;
    }

    @Override
    public UploadPack createUploadPack(String fullRepositoryName) throws IOException, InterruptedException {
        if (isMine(fullRepositoryName))
            return new UploadPack(repo.openRepository());
        return null;
    }

    private boolean isMine(String name) {
        if (name.startsWith("/"))
            name = name.substring(1);
        return name.equals(repo.getUrlName());
    }
}
