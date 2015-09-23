package org.jenkinsci.plugins.workflow.cps.global;

import com.google.inject.Inject;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Util;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.mozilla.javascript.tools.shell.Global;

import java.io.File;
import java.net.URL;
import java.util.List;

import static org.jenkinsci.plugins.workflow.cps.global.UserDefinedGlobalVariable.PREFIX;

/**
 * @author Kohsuke Kawaguchi
 */
public class UserDefinedGlobalVariableListTest extends Assert {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Inject
    WorkflowLibRepository repo;

    @Before
    public void setUp() {
        j.jenkins.getInjector().injectMembers(this);
    }

    /**
     * If the user creates/removes files from the repository, it should appear/disappear from the extension list.
     */
    @Test
    public void rebuild() throws Exception {
        // initial clone
        CloneCommand clone = Git.cloneRepository();
        clone.setURI(new URL(j.getURL(), "workflowLibs.git").toExternalForm());
        File dir = tmp.newFolder();
        clone.setDirectory(dir);
        Git git = clone.call();

        FilePath src =new FilePath(new File(dir, PREFIX));
        src.child("acme.groovy").write("// empty", "UTF-8");
        src.child("acme.txt").write("Plain\ntext<", "UTF-8");

        UserDefinedGlobalVariable acme = new UserDefinedGlobalVariable(repo, "acme");

        // this variable to become accessible once the new definition is pushed
        git.add().addFilepattern(".").call();
        commitAndPush(git);
        assertTrue(current().contains(acme));

        // help
        assertEquals("Plain<br>text&lt;", acme.getHelpHtml());

        // and if the file is removed it should disappear
        src.child("acme.groovy").delete();
        git.rm().addFilepattern(PREFIX+"/acme.groovy").call();
        commitAndPush(git);
        assertFalse(current().contains(acme));
    }

    private void commitAndPush(Git git) throws GitAPIException {
        git.commit().setMessage("changed").call();
        git.push().call();
    }

    private List<UserDefinedGlobalVariable> current() {
        return Util.filter(GlobalVariable.ALL, UserDefinedGlobalVariable.class);
    }
}