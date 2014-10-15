package org.jenkinsci.plugins.workflow.cps.global

import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.test.steps.WatchYourStep
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.RestartableJenkinsRule

import javax.inject.Inject

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class WorkflowLibRepositoryTest {
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
    @Test
    public void globalLib() throws Exception {
        story.step {
            new File(repo.workspace,"Foo.groovy").text = """
public class Foo implements Serializable {
  def answer(dsl) {
    dsl.println "control"
    dsl.watch new File("${jenkins.rootPath}/go");
    return 42;
  }
}
"""

            WorkflowJob p = jenkins.createProject(WorkflowJob.class, "p");

            p.definition = new CpsFlowDefinition("""
              o=new Foo().answer(this)
              println "o="+o;
            """);

            // get the build going
            def q = p.scheduleBuild2(0);

            WorkflowRun b = q.startCondition.get()
            CpsFlowExecution e = b.executionPromise.get()

            // wait until the executor gets assigned and the execution pauses at the watch step
            while (watchDescriptor.activeWatches.isEmpty() && b.isBuilding())
                e.waitForSuspension();

            assert b.isBuilding() : b.log
            assert b.log.contains("control\n")
        }

        story.step {
            WorkflowJob p = jenkins.getItem("p");
            WorkflowRun b = p.getBuildByNumber(1);
            CpsFlowExecution e = b.executionPromise.get()

            // resume from where it left off
            jenkins.rootPath.child("go").touch(0)
            watchDescriptor.watchUpdate()

            // wait until the completion
            while (b.isBuilding())
                e.waitForSuspension()

            story.j.assertBuildStatusSuccess(b);

            println b.log
            assert b.log.contains("o=42\n");
        }
    }
}
