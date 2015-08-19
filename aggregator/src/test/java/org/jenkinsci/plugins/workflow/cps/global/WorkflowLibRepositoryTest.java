package org.jenkinsci.plugins.workflow.cps.global;

import java.io.File;
import java.util.Arrays;

import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import javax.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
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
    WorkflowLibRepository repo;

    @Inject
    UserDefinedGlobalVariableList uvl;

    /**
     * Have some global libs
     */
    @Test
    public void globalLib() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                File dir = new File(repo.workspace,"src/foo");
                dir.mkdirs();

                FileUtils.write(new File(dir, "Foo.groovy"),
                        "package foo;\n" +
                        "def answer() {\n" +
                        "  echo 'running the answer method'\n" +
                        "  semaphore 'watch'\n" +
                        "  return 42;\n" +
                        "}");

                WorkflowJob p = jenkins.createProject(WorkflowJob.class, "p");

                p.setDefinition(new CpsFlowDefinition(
                        "o=new foo.Foo().answer()\n" +
                        "println 'o=' + o;"));

                // get the build going
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

                // wait until the executor gets assigned and the execution pauses
                SemaphoreStep.waitForStart("watch/1", b);
                e.waitForSuspension();

                assertTrue(JenkinsRule.getLog(b), b.isBuilding());
                story.j.assertLogContains("running the answer method", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

                // resume from where it left off
                SemaphoreStep.success("watch/1", null);

                // wait until the completion
                while (b.isBuilding())
                    e.waitForSuspension();

                story.j.assertBuildStatusSuccess(b);

                story.j.assertLogContains("o=42", b);
            }
        });
    }

    /**
     * User can define global variables.
     */
    @Test
    public void userDefinedGlobalVariable() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                File vars = new File(repo.workspace, UserDefinedGlobalVariable.PREFIX);
                vars.mkdirs();
                FileUtils.writeStringToFile(new File(vars, "acmeVar.groovy"), StringUtils.join(Arrays.asList(
                        "def hello(name) {echo \"Hello ${name}\"}",
                        "def foo(x) { this.x = x+'-set'; }",
                        "def bar() { return x+'-get' }")
                        , "\n"));
                FileUtils.writeStringToFile(new File(vars, "acmeFunc.groovy"), StringUtils.join(Arrays.asList(
                        "def call(a,b) { echo \"call($a,$b)\" }")
                        , "\n"));
                FileUtils.writeStringToFile(new File(vars, "acmeBody.groovy"), StringUtils.join(Arrays.asList(
                        "def call(body) { ",
                        "  def config = [:]",
                        "  body.resolveStrategy = Closure.DELEGATE_FIRST",
                        "  body.delegate = config",
                        "  body()",
                        "  echo 'title was '+config.title",
                        "}")
                        , "\n"));

                // simulate the effect of push
                uvl.rebuild();

                WorkflowJob p = jenkins.createProject(WorkflowJob.class, "p");

                p.setDefinition(new CpsFlowDefinition(
                        "acmeVar.hello('Workflow');" +
                        "acmeVar.foo('seed');" +
                        "echo '['+acmeVar.bar()+']';"+
                        "acmeFunc(1,2);"+
                        "acmeBody { title = 'yolo' }",
                    true));

                // build this workflow
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));

                story.j.assertLogContains("Hello Workflow", b);
                story.j.assertLogContains("[seed-set-get]", b);
                story.j.assertLogContains("call(1,2)", b);
                story.j.assertLogContains("title was yolo", b);
            }
        });
    }
}
