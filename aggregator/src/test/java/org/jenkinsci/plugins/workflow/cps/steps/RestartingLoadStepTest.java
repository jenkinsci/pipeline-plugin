package org.jenkinsci.plugins.workflow.cps.steps;

import javax.inject.Inject;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.WatchYourStep;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class RestartingLoadStepTest {
    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Inject
    Jenkins jenkins;

    @Inject
    WatchYourStep.DescriptorImpl watchDescriptor;

    /**
     * Makes sure that loaded scripts survive persistence.
     */
    @Test
    public void persistenceOfLoadedScripts() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = jenkins.createProject(WorkflowJob.class, "p");
                jenkins.getWorkspaceFor(p).child("test.groovy").write(
                    "def answer(i) { return i*2; }\n" +
                    "def foo() {\n" +
                    "    def i=21;\n" +
                    "    watch new File('" + jenkins.getRootPath() + "/go');\n" +
                    "    return answer(i);\n" +
                    "}\n" +
                    "return this;", null);
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  println 'started'\n" +
                    "  def o = load 'test.groovy'\n" +
                    "  println 'o=' + o.foo();\n" +
                    "}"));

                // get the build going
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

                // wait until the executor gets assigned and the execution pauses at the watch step
                while (watchDescriptor.getActiveWatches().isEmpty() && b.isBuilding())
                    e.waitForSuspension();

                assertTrue(JenkinsRule.getLog(b), b.isBuilding());
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

                // resume from where it left off
                jenkins.getRootPath().child("go").touch(0);
                watchDescriptor.watchUpdate();

                // wait until the completion
                while (b.isBuilding())
                    e.waitForSuspension();

                story.j.assertBuildStatusSuccess(b);

                story.j.assertLogContains("o=42\n", b);
            }
        });
    }

    /**
     * The load command itself can block while it executes the script
     */
    @Test
    public void pauseInsideLoad() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = jenkins.createProject(WorkflowJob.class, "p");
                jenkins.getWorkspaceFor(p).child("test.groovy").write(
                    "def answer(i) { return i*2; }\n" +
                    "def i=21;\n" +
                    "watch new File('" + jenkins.getRootPath() + "/go');\n" +
                    "return answer(i);\n", null);
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  println 'started'\n" +
                    "  def o = load 'test.groovy'\n" +
                    "  println 'o=' + o;\n" +
                    "}"));

                // get the build going
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

                // wait until the executor gets assigned and the execution pauses at the watch step
                while (watchDescriptor.getActiveWatches().isEmpty() && b.isBuilding())
                    e.waitForSuspension();

                assertTrue(JenkinsRule.getLog(b), b.isBuilding());
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

                // resume from where it left off
                jenkins.getRootPath().child("go").touch(0);
                watchDescriptor.watchUpdate();

                // wait until the completion
                while (b.isBuilding())
                    e.waitForSuspension();

                story.j.assertBuildStatusSuccess(b);

                story.j.assertLogContains("o=42\n", b);
            }
        });
    }
}
