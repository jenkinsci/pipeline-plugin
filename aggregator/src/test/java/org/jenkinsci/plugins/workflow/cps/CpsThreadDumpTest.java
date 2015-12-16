package org.jenkinsci.plugins.workflow.cps;

import java.util.ArrayList;
import static java.util.Arrays.asList;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.JenkinsRuleExt;
import org.jenkinsci.plugins.workflow.cps.CpsThreadDump.ThreadInfo;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class CpsThreadDumpTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule j = JenkinsRuleExt.diagnoseJenkins30395();
    private WorkflowJob p;

    @Before
    public void setUp() throws Exception {
        p = j.jenkins.createProject(WorkflowJob.class, "p");
    }

    @Test
    public void simple() throws Exception {
        p.setDefinition(new CpsFlowDefinition(StringUtils.join(asList(
                "def foo() { bar() }",
                "def bar() {",
                "   semaphore 'x'",
                "}",
                "foo()"
        ), "\n")));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("x/1", b);
        CpsThreadDump td = b.getAction(CpsThreadDumpAction.class).threadDumpSynchronous();
        td.print(System.out);

        {// verify that we got the right thread dump
            List<ThreadInfo> threads = td.getThreads();
            assertEquals(1, threads.size());
            ThreadInfo t = threads.get(0);
            assertEquals("Thread #0", t.getHeadline());
            assertStackTrace(t,
                    "DSL.semaphore(Native Method)",
                    "WorkflowScript.bar(WorkflowScript:3)",
                    "WorkflowScript.foo(WorkflowScript:1)",
                    "WorkflowScript.run(WorkflowScript:5)");
        }
        SemaphoreStep.success("x/1", null);
        j.waitForCompletion(b);
        assertNull(b.getAction(CpsThreadDumpAction.class));
    }

    @Test
    public void parallel() throws Exception {
        p.setDefinition(new CpsFlowDefinition(StringUtils.join(asList(
                "def foo(x) { bar(x) }",// 1
                "def bar(x) {",
                "   semaphore x",       // 3
                "}",
                "def zot() {",
                "  parallel(",          // 6
                "    b1:{ foo('x') },",
                "    b2:{ bar('y') });",
                "}",
                "zot()"                 // 10
        ), "\n")));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("x/1", b);
        SemaphoreStep.waitForStart("y/1", b);

        CpsThreadDump td = b.getAction(CpsThreadDumpAction.class).threadDumpSynchronous();
        td.print(System.out);

        assertStackTrace( td.getThreads().get(0),
            "DSL.semaphore(Native Method)",
            "WorkflowScript.bar(WorkflowScript:3)",
            "WorkflowScript.foo(WorkflowScript:1)",
            "WorkflowScript.zot(WorkflowScript:7)",
            "DSL.parallel(Native Method)",
            "WorkflowScript.zot(WorkflowScript:6)",
            "WorkflowScript.run(WorkflowScript:10)");

        assertStackTrace( td.getThreads().get(1),
            "DSL.semaphore(Native Method)",
            "WorkflowScript.bar(WorkflowScript:3)",
            "WorkflowScript.zot(WorkflowScript:8)");
    }

    @Test public void load() throws Exception {
        j.jenkins.getWorkspaceFor(p).child("lib.groovy").write("def m() {semaphore 'here'}; this", null);
        p.setDefinition(new CpsFlowDefinition("def lib; node {lib = load 'lib.groovy'}; lib.m()", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("here/1", b);
        CpsThreadDump td = b.getAction(CpsThreadDumpAction.class).threadDumpSynchronous();
        td.print(System.out);
        assertStackTrace(td.getThreads().get(0),
            "DSL.semaphore(Native Method)",
            "Script1.m(Script1.groovy:1)",
            "WorkflowScript.run(WorkflowScript:1)");
    }

    @Test public void nativeMethods() throws Exception {
        p.setDefinition(new CpsFlowDefinition(
            "@NonCPS def untransformed() {Thread.sleep(Long.MAX_VALUE)}\n" +
            "def helper() {echo 'sleeping'; /* flush output */ sleep 1; untransformed()}\n" +
            "helper()"));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();
        j.waitForMessage("sleeping", b);
        do { // wait for the CPS VM to be busy (opposite of waitForSuspension)
            Thread.sleep(100);
        } while (!e.blocksRestart());
        CpsThreadDump td = e.getThreadDump();
        td.print(System.out);
        assertStackTrace(td.getThreads().get(0),
            // TODO would like to see untransformed and Thread.sleep here
            "WorkflowScript.helper(WorkflowScript:2)",
            "WorkflowScript.run(WorkflowScript:3)");
        b.doKill();
    }

    private void assertStackTrace(ThreadInfo t, String... expected) {
        assertEquals(asList(expected), toString(t.getStackTrace()));
    }

    private List<String> toString(List<StackTraceElement> in) {
        List<String> r = new ArrayList<String>();
        for (StackTraceElement e : in)
            r.add(e.toString());
        return r;
    }
}
