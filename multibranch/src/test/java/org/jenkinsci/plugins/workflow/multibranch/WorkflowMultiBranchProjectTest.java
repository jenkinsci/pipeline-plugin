/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.multibranch;

import hudson.model.DescriptorVisibilityFilter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchPropertyDescriptor;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.scm.GitSampleRepoRule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class WorkflowMultiBranchProjectTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Test public void basicBranches() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file')}");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertEquals(new SCMHead("master"), SCMHead.HeadByItem.findHead(p));
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        r.assertLogContains("initial content", b1);
        r.assertLogContains("branch=master", b1);
        sampleRepo.git("checkout", "-b", "feature");
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase()}");
        ScriptApproval.get().approveSignature("method java.lang.String toUpperCase");
        sampleRepo.write("file", "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");
        p = scheduleAndFindBranchProject(mp, "feature");
        assertEquals(2, mp.getItems().size());
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        r.assertLogContains("SUBSEQUENT CONTENT", b1);
        r.assertLogContains("branch=feature", b1);
    }

    // TODO commit notifications can both add branch projects and build them
    // TODO scheduled reindexing can add branch projects
    // TODO regular polling works on branch projects
    // TODO changelog shows per-branch changes

    static @Nonnull WorkflowJob scheduleAndFindBranchProject(@Nonnull WorkflowMultiBranchProject mp, @Nonnull String name) throws Exception {
        mp.scheduleBuild2(0).getFuture().get();
        return findBranchProject(mp, name);
    }

    static @Nonnull WorkflowJob findBranchProject(@Nonnull WorkflowMultiBranchProject mp, @Nonnull String name) throws Exception {
        WorkflowJob p = mp.getItem(name);
        if (p == null) {
            mp.getIndexing().writeWholeLogTo(System.out);
            fail(name + " project not found");
        }
        return p;
    }

    @Test public void visibleBranchProperties() throws Exception {
        WorkflowMultiBranchProject p = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        Set<Class<? extends BranchProperty>> clazzes = new HashSet<Class<? extends BranchProperty>>();
        for (BranchPropertyDescriptor d : DescriptorVisibilityFilter.apply(p, BranchPropertyDescriptor.all())) {
            clazzes.add(d.clazz);
        }
        // RateLimitBranchProperty & BuildRetentionBranchProperty hidden by JobPropertyStep.HideSuperfluousBranchProperties.
        // UntrustedBranchProperty hidden because it applies only to Project.
        assertEquals(Collections.<Class<? extends BranchProperty>>emptySet(), clazzes);
    }

}
