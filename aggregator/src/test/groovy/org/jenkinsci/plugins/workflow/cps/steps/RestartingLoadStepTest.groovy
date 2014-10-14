package org.jenkinsci.plugins.workflow.cps.steps

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
class RestartingLoadStepTest {
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
        story.step {
            WorkflowJob p = jenkins.createProject(WorkflowJob.class, "p");
            jenkins.getWorkspaceFor(p).child("test.groovy").write("""
                def answer(i) { return i*2; }

                def foo() {
                    def i=21;
                    watch new File("${jenkins.rootPath}/go");
                    return answer(i);
                }

                return this;
            ""","UTF-8");

            p.definition = new CpsFlowDefinition("""
                node {
                  println "started"
                  def o = load 'test.groovy'
                  println "o="+o.foo();
                }
            """);

            // get the build going
            def q = p.scheduleBuild2(0);

            WorkflowRun b = q.startCondition.get()
            CpsFlowExecution e = b.executionPromise.get()

            // wait until the executor gets assigned and the execution pauses at the watch step
            while (watchDescriptor.activeWatches.isEmpty() && b.isBuilding())
                e.waitForSuspension();

            assert b.isBuilding() : b.log
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

    /**
     * The load command itself can block while it executes the script
     */
    @Test
    public void pauseInsideLoad() throws Exception {
        story.step {
            WorkflowJob p = jenkins.createProject(WorkflowJob.class, "p");
            jenkins.getWorkspaceFor(p).child("test.groovy").write("""
                def answer(i) { return i*2; }

                def i=21;
                watch new File("${jenkins.rootPath}/go");
                return answer(i);
            ""","UTF-8");

            p.definition = new CpsFlowDefinition("""
                node {
                  println "started"
                  def o = load 'test.groovy'
                  println "o="+o;
                }
            """);

            // get the build going
            def q = p.scheduleBuild2(0);

            WorkflowRun b = q.startCondition.get()
            CpsFlowExecution e = b.executionPromise.get()

            // wait until the executor gets assigned and the execution pauses at the watch step
            while (watchDescriptor.activeWatches.isEmpty() && b.isBuilding())
                e.waitForSuspension();

            assert b.isBuilding() : b.log
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
