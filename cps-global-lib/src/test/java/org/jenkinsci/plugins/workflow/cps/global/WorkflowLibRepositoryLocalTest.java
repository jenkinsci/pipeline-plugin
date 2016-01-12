package org.jenkinsci.plugins.workflow.cps.global;

import com.google.inject.Inject;
import hudson.util.FormValidation;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinitionValidator;
import org.junit.Assert;
import org.junit.Before;
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

    @Inject
    CpsFlowDefinition.DescriptorImpl cfdd;

    @Before
    public void setUp() {
        j.jenkins.getInjector().injectMembers(this);
    }

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

        FileUtils.writeStringToFile(f,"package org.acme; def hello() { echo('answer is 42') }");

        // commit & push
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Initial commit").call();
        git.push().call();

        // test if this script is accessible from form validation
        assertEquals(cfdd.doCheckScriptCompile("import org.acme.Foo"), CpsFlowDefinitionValidator.CheckStatus.SUCCESS.asJSON());
        assertNotEquals(cfdd.doCheckScriptCompile("import org.acme.NoSuchThing").toString(), CpsFlowDefinitionValidator.CheckStatus.SUCCESS.asJSON().toString()); // control test
        // valid from script-security point of view
        assertSame(cfdd.doCheckScript("import org.acme.Foo", true), FormValidation.ok());
        assertSame(cfdd.doCheckScript("import org.acme.NoSuchThing", true), FormValidation.ok());
    }
}