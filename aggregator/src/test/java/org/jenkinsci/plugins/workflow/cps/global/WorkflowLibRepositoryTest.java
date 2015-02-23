package org.jenkinsci.plugins.workflow.cps.global;

import java.io.File;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.WatchYourStep;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.RandomlyFails;
import javax.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class WorkflowLibRepositoryTest {
    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Inject
    Jenkins jenkins;

    @Inject
    WatchYourStep.DescriptorImpl watchDescriptor;

    @Inject
    WorkflowLibRepository repo;

    /**
     * Have some global libs
     */
    @RandomlyFails("TODO periodic failures: p #1 log is just 'Started'")
    @Test
    public void globalLib() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                File dir = new File(repo.workspace,"src/foo");
                dir.mkdirs();

                FileUtils.write(new File(dir, "Foo.groovy"),
                        "package foo;\n" +
                        "def answer() {\n" +
                        "  println 'control'\n" +
                        "  watch new File('" + jenkins.getRootPath() + "/go')\n" +
                        "  return 42;\n" +
                        "}");

                WorkflowJob p = jenkins.createProject(WorkflowJob.class, "p");

                p.setDefinition(new CpsFlowDefinition(
                        "o=new foo.Foo().answer()\n" +
                        "println 'o=' + o;"));

                // get the build going
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

                // wait until the executor gets assigned and the execution pauses at the watch step
                while (watchDescriptor.getActiveWatches().isEmpty() && b.isBuilding())
                    e.waitForSuspension();

                assertTrue(JenkinsRule.getLog(b), b.isBuilding());
                story.j.assertLogContains("control\n", b);
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
