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

import hudson.model.Result;
import hudson.model.Slave;
import hudson.model.queue.QueueTaskFuture;
import java.util.Collections;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.cps.AbstractCpsFlowTest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.RetryStep;
import org.jenkinsci.plugins.workflow.support.actions.LogActionImpl;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Test of {@link WorkflowJob} that doesn't involve Jenkins restarts.
 *
 * @author Kohsuke Kawaguchi
 */
public class WorkflowJobNonRestartingTest extends AbstractCpsFlowTest {

    WorkflowJob p;

    @Before public void setUp() throws Exception {
        super.setUp();
        p = jenkins.jenkins.createProject(WorkflowJob.class, "demo");
    }

    @Test
    public void shellStep() throws Exception {
        p.setDefinition(new CpsFlowDefinition("node {sh 'echo hello world'}"));

        QueueTaskFuture<WorkflowRun> f = p.scheduleBuild2(0);
        WorkflowRun b = f.getStartCondition().get();

        CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();
        e.waitForSuspension();

        Thread.sleep(1000);  // give a bit of time for shell script to complete

        while (!e.isComplete()) {
            e.waitForSuspension();   // let the workflow run to the completion
        }

        f.get();
        jenkins.assertBuildStatusSuccess(b);
        // currentHeads[0] is FlowEndNode, whose parent is BlockEndNode for "node",
        // whose parent is BlockEndNode for body invocation, whose parent is AtomNode
        AtomNode atom = (AtomNode) e.getCurrentHeads().get(0).getParents().get(0).getParents().get(0).getParents().get(0);
        LogActionImpl la = (LogActionImpl) atom.getAction(LogAction.class);
        String text = FileUtils.readFileToString(la.getLogFile());
        assertTrue(text, text.contains("hello world"));
    }

    /**
     * Obtains a node.
     */
    @Test
    public void nodeLease() throws Exception {
        Slave s = jenkins.createSlave();
        s.getComputer().connect(false).get(); // wait for the slave to fully get connected

        p.setDefinition(new CpsFlowDefinition(
           "node {println 'Yo!'}\n" +
           "println 'Out!'"
        ));

        WorkflowRun b = p.scheduleBuild2(0).get();

        jenkins.assertBuildStatusSuccess(b);
        jenkins.assertLogContains("Yo!", b);
        jenkins.assertLogContains("Out!", b);
        String log = JenkinsRule.getLog(b);
        assertTrue(log.indexOf("Yo!") < log.indexOf("Out!"));
    }

    /**
     * Test the {@link RetryStep}.
     */
    @Test
    public void testRetry() throws Exception {
        p.setDefinition(new CpsFlowDefinition(
            "int i = 0;\n" +
            "retry(3) {\n" +
            "    println 'Trying!'\n" +
            "    if (i++ < 2) error('oops');\n" +
            "    println 'Done!'\n" +
            "}\n" +
            "println 'Over!'"
        ));

        QueueTaskFuture<WorkflowRun> f = p.scheduleBuild2(0);
        WorkflowRun b = jenkins.assertBuildStatusSuccess(f);

        String log = JenkinsRule.getLog(b);
        jenkins.assertLogNotContains("\tat ", b);
        System.err.println(log);

        int idx = 0;
        for (String msg : new String[] {
            "Trying!",
            "oops",
            "Retrying",
            "Trying!",
            "oops",
            "Retrying",
            "Trying!",
            "Done!",
            "Over!",
        }) {
            idx = log.indexOf(msg, idx + 1);
            assertTrue(msg + " not found", idx != -1);
        }

        idx = 0;
        for (String msg : new String[] {
            "[Workflow] Retry the body up to N times : Start",
            "[Workflow] retry {",
            "[Workflow] } //retry",
            "[Workflow] retry {",
            "[Workflow] } //retry",
            "[Workflow] retry {",
            "[Workflow] } //retry",
            "[Workflow] Retry the body up to N times : End",
        }) {
            idx = log.indexOf(msg, idx + 1);
            assertTrue(msg + " not found", idx != -1);
        }
    }

    /**
     * The first test case to try out the sandbox execution.
     */
    @Test
    public void sandbox() throws Exception {
        p.setDefinition(new CpsFlowDefinition(
            "def message() {'hello world'}\n" +
            "node {\n" +
            "  sh('echo ' + message())\n" +
            "}\n", true));

        WorkflowRun b = jenkins.assertBuildStatusSuccess(p.scheduleBuild2(0));

        // currentHeads[0] is FlowEndNode, whose parent is BlockEndNode for "node",
        // whose parent is BlockEndNode for body invocation, whose parent is AtomNode
        AtomNode atom = (AtomNode) b.getExecution().getCurrentHeads().get(0).getParents().get(0).getParents().get(0).getParents().get(0);
        LogActionImpl la = (LogActionImpl) atom.getAction(LogAction.class);
        String text = FileUtils.readFileToString(la.getLogFile());
        assertTrue(text, text.contains("hello world"));
    }

    /**
     * If a prohibited method is called, execution should fail.
     */
    @Issue("JENKINS-26541")
    @Test
    public void sandboxRejection() throws Exception {
        assertRejected("Jenkins.getInstance()");
        assertRejected("parallel(main: {Jenkins.getInstance()})");
        assertRejected("parallel(main: {parallel(main2: {Jenkins.getInstance()})})");
        assertRejected("node {parallel(main: {ws {parallel(main2: {ws {Jenkins.getInstance()}})}})}");
    }
    private void assertRejected(String script) throws Exception {
        String signature = "staticMethod jenkins.model.Jenkins getInstance";
        ScriptApproval scriptApproval = ScriptApproval.get();
        scriptApproval.denySignature(signature);
        assertEquals(Collections.emptySet(), scriptApproval.getPendingSignatures());
        p.setDefinition(new CpsFlowDefinition(script, true));
        WorkflowRun b = p.scheduleBuild2(0).get();
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use " + signature, b);
        jenkins.assertBuildStatus(Result.FAILURE, b);
        Set<ScriptApproval.PendingSignature> pendingSignatures = scriptApproval.getPendingSignatures();
        assertEquals(script, 1, pendingSignatures.size());
        assertEquals(signature, pendingSignatures.iterator().next().signature);

    }

    /**
     * Trying to run a step without having the required context should result in a graceful error.
     */
    @Test
    public void missingContextCheck() throws Exception {
        p.setDefinition(new CpsFlowDefinition("sh 'true'", true));

        WorkflowRun b = p.scheduleBuild2(0).get();

        jenkins.assertLogContains("such as: node", b); // make sure the 'node' is a suggested message. this comes from MissingContextVariableException
//        jenkins.assertLogNotContains("Exception", b)   // haven't figured out how to hide this
        jenkins.assertBuildStatus(Result.FAILURE, b);
    }
}
