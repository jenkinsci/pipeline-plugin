package org.jenkinsci.plugins.workflow.cps.global;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.net.URL;
import java.util.Collection;

public class WorkflowLibRepositoryLocalTest extends Assert {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    @Issue("JENKINS-25632")
    public void initialRepoShouldBeEmpty() throws Exception {
        // initial clone
        CloneCommand clone = Git.cloneRepository();
        clone.setURI(new URL(j.getURL(), "workflowLibs.git").toExternalForm());
        File dir = tmp.newFolder();
        clone.setDirectory(dir);
        Git git = clone.call();

        // makes sure there's nothing in here
        Collection<Ref> remotes = git.lsRemote().setRemote("origin").call();
        assertEquals(0,remotes.size());

        File f = new File(dir, "src/org/acme/Foo.groovy");
        f.getParentFile().mkdirs();

        FileUtils.writeStringToFile(f,"def hello() { echo('answer is 42') }");

        // commit & push
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Initial commit").call();
        git.push().call();
    }
}