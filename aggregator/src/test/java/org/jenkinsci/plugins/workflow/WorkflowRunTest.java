/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow;

import hudson.model.BallColor;
import hudson.model.Item;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.User;
import hudson.model.queue.QueueTaskFuture;
import hudson.security.ACL;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.Permission;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

public class WorkflowRunTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();

    WorkflowJob p;

    @Before
    public void setUp() throws Exception {
        r.jenkins.getInjector().injectMembers(this);
    }

    @Test public void basics() throws Exception {
        p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("println('hello')"));
        WorkflowRun b1 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertFalse(b1.isBuilding());
        // TODO protected (but !building -> !inProgress): assertFalse(b1.isInProgress());
        assertFalse(b1.isLogUpdated());
        assertTrue(b1.getDuration() > 0);
        WorkflowRun b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(b1, b2.getPreviousBuild());
        assertEquals(null, b1.getPreviousBuild());
        assertTrue(b1.getLog().contains("hello\n"));
    }

    @Test public void parameters() throws Exception {
        p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {sh('echo param=' + PARAM)}",true));
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("PARAM", null)));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("PARAM", "value"))));
        r.assertLogContains("param=value", b);
    }

    @Test public void funnyParameters() throws Exception {
        p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("echo \"a.b=${binding['a.b']}\"", /* TODO Script.binding does not work in sandbox */false));
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("a.b", null)));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("a.b", "v"))));
        r.assertLogContains("a.b=v", b);
    }

    /**
     * Verifies that {@link WorkflowRun#getIconColor()} returns the right color.
     */
    @Test
    public void iconColor() throws Exception {
        p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
            "println('hello')\n"+
            "semaphore 'wait'\n"+
            "println('hello')\n"
        ));

        // no build exists yet
        assertSame(p.getIconColor(),BallColor.NOTBUILT);

        // get a build going
        QueueTaskFuture<WorkflowRun> q = p.scheduleBuild2(0);
        WorkflowRun b1 = q.getStartCondition().get();
        CpsFlowExecution e = (CpsFlowExecution) b1.getExecutionPromise().get();

        // initial state should be blinking gray
        assertFalse(b1.hasntStartedYet());
        assertColor(b1, BallColor.NOTBUILT_ANIME);

        SemaphoreStep.waitForStart("wait/1", b1);

        // at the pause point, it should be still blinking gray
        assertFalse(b1.hasntStartedYet());
        assertColor(b1, BallColor.NOTBUILT_ANIME);

        SemaphoreStep.success("wait/1", null);

        // bring it to the completion
        q.get(5, TimeUnit.SECONDS);
        assertTrue(e.isComplete());

        // and the color should be now solid blue
        assertFalse(b1.hasntStartedYet());
        assertColor(b1, BallColor.BLUE);

        // get another one going
        q = p.scheduleBuild2(0);
        WorkflowRun b2 = q.getStartCondition().get();
        e = (CpsFlowExecution) b2.getExecutionPromise().get();

        // initial state should be blinking blue because the last one was blue
        assertFalse(b2.hasntStartedYet());
        assertColor(b2, BallColor.BLUE_ANIME);

        SemaphoreStep.waitForStart("wait/2", b2);

        // at the pause point, it should be still blinking gray
        assertFalse(b2.hasntStartedYet());
        assertColor(b2, BallColor.BLUE_ANIME);

        // bring it to the completion
        SemaphoreStep.success("wait/2", null);
        q.get(5, TimeUnit.SECONDS);

        // and the color should be now solid blue
        assertFalse(b2.hasntStartedYet());
        assertColor(b2, BallColor.BLUE);
    }

    @Test public void scriptApproval() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        GlobalMatrixAuthorizationStrategy gmas = new GlobalMatrixAuthorizationStrategy();
        gmas.add(Jenkins.READ, "devel");
        for (Permission p : Item.PERMISSIONS.getPermissions()) {
            gmas.add(p, "devel");
        }
        r.jenkins.setAuthorizationStrategy(gmas);
        p = r.jenkins.createProject(WorkflowJob.class, "p");
        final String groovy = "println 'hello'";
        ACL.impersonate(User.get("devel").impersonate(), new Runnable() {
            @Override public void run() {
                p.setDefinition(new CpsFlowDefinition(groovy));
            }
        });
        r.assertLogContains("UnapprovedUsageException", r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get()));
        Set<ScriptApproval.PendingScript> pendingScripts = ScriptApproval.get().getPendingScripts();
        assertEquals(1, pendingScripts.size());
        ScriptApproval.PendingScript pendingScript = pendingScripts.iterator().next();
        assertEquals(groovy, pendingScript.script);
        // only works if configured via WebClient: assertEquals(p, pendingScript.getContext().getItem());
        assertEquals("devel", pendingScript.getContext().getUser());
        ScriptApproval.get().approveScript(pendingScript.getHash());
        r.assertLogContains("hello", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    private void assertColor(WorkflowRun b, BallColor color) throws IOException {
        assertSame(b.getLog(), color, b.getIconColor());
        assertSame(b.getLog(), color, p.getIconColor());
    }

    @Test @Issue("JENKINS-25630")
    public void contextInjectionOfSubParameters() throws Exception {
        // see SubtypeInjectingStep
        p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node('master') { injectSubtypesAsContext() }"));
        r.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }

    @Issue("JENKINS-27531")
    @LocalData
    @Test public void loadMigratedBuildRecord() throws Exception {
        WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
        assertNotNull(p);
        WorkflowRun b = p.getLastBuild();
        assertNotNull(b);
        r.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b));
    }

}
