package org.jenkinsci.plugins.workflow.cps;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.cps.CpsThreadDump.ThreadInfo;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class CpsThreadDumpTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    private WorkflowJob p;

    @Before
    public void setUp() throws Exception {
        p = j.jenkins.createProject(WorkflowJob.class, "p");
    }

    @Test
    public void threadDump() throws Exception {
        p.setDefinition(new CpsFlowDefinition(StringUtils.join(asList(
                "def foo() { bar() }",
                "def bar() {",
                "   semaphore 'x'",
                "}",
                "foo()"
        ), "\n")));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("x/1", b);
        CpsFlowExecution e = (CpsFlowExecution) b.getExecution();
        CpsThreadDump td = e.getThreadDump();
        td.print(System.out);

        {// verify that we got the right thread dump
            List<ThreadInfo> threads = td.getThreads();
            assertEquals(1, threads.size());
            ThreadInfo t = threads.get(0);
            assertThat(t.getHeadline(), containsString("Thread #0"));
            assertStackTrace(t,
                    "DSL.semaphore(Native Method)",
                    "WorkflowScript.bar(WorkflowScript:3)",
                    "WorkflowScript.foo(WorkflowScript:1)",
                    "WorkflowScript.run(WorkflowScript:5)");
        }
    }

    @Test
    public void threadDump2() throws Exception {
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

        CpsFlowExecution e = (CpsFlowExecution) b.getExecution();
        CpsThreadDump td = e.getThreadDump();
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

    private void assertStackTrace(ThreadInfo t, String... expected) {
        assertThat(toString(t.getStackTrace()), is(asList(expected)));
    }

    private List<String> toString(List<StackTraceElement> in) {
        List<String> r = new ArrayList<String>();
        for (StackTraceElement e : in)
            r.add(e.toString());
        return r;
    }
}
