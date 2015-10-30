package org.jenkinsci.plugins.workflow;

import hudson.model.Result;
import hudson.slaves.DumbSlave;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import static org.junit.Assert.assertTrue;
import org.junit.Ignore;
import org.jvnet.hudson.test.Issue;

/**
 * Tests related to serialization of program state.
 */
public class SerializationTest extends SingleJobTestBase {

    /**
     * When wokflow execution runs into a serialization problem, can we handle that situation gracefully?
     */
    @Test
    public void stepExecutionFailsToPersist() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                        "node {",
                        "  persistenceProblem()",
                        "}"
                )));

                startBuilding();
                waitForWorkflowToSuspend();

                // TODO: let the ripple effect of a failure run to the completion.
                while (b.isBuilding())
                    try {
                        waitForWorkflowToSuspend();
                    } catch (Exception x) {
                        // ignore persistence failure
                        if (!x.getMessage().contains("Failed to persist"))
                            throw x;
                    }

                story.j.assertBuildStatus(Result.FAILURE, b);
                story.j.assertLogContains("java.lang.RuntimeException: testing the forced persistence failure behaviour", b);
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                rebuildContext(story.j);

                story.j.assertBuildStatus(Result.FAILURE, b);
            }
        });
    }
    /**
     * {@link Step} that fails to persist. Used to test the behaviour of error reporting/recovery.
     */
    public static class PersistenceProblemStep extends AbstractStepImpl {
        @DataBoundConstructor
        public PersistenceProblemStep() {
            super();
        }
        @TestExtension("stepExecutionFailsToPersist")
        public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
            public DescriptorImpl() {
                super(PersistenceProblemStepExecution.class);
            }
            @Override
            public String getFunctionName() {
                return "persistenceProblem";
            }
            @Override
            public String getDisplayName() {
                return "Problematic Persistence";
            }
        }
        /**
         * {@link StepExecution} that fails to serialize.
         *
         * Used to test the error recovery path of {@link WorkflowJob}.
         */
        public static class PersistenceProblemStepExecution extends AbstractStepExecutionImpl {
            public final Object notSerializable = new Object();
            private Object writeReplace() {
                throw new RuntimeException("testing the forced persistence failure behaviour");
            }
            @Override
            public boolean start() throws Exception {
                return false;
            }
            @Override
            public void stop(Throwable cause) throws Exception {
                // nothing to do here
            }
        }
    }

    /**
     * Workflow captures a stateful object, and we verify that it survives the restart
     */
    @Test public void persistEphemeralObject() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                jenkins().setNumExecutors(0);
                DumbSlave s = createSlave(story.j);
                String nodeName = s.getNodeName();

                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                    "def s = jenkins.model.Jenkins.instance.getComputer('" + nodeName + "')\n" +
                    "def r = s.node.rootPath\n" +
                    "def p = r.getRemote()\n" +

                    "semaphore 'wait'\n" +

                    // make sure these values are still alive
                    "assert s.nodeName=='" + nodeName + "'\n" +
                    "assert r.getRemote()==p : r.getRemote() + ' vs ' + p;\n" +
                    "assert r.channel==s.channel : r.channel.toString() + ' vs ' + s.channel\n"));

                startBuilding();
                SemaphoreStep.waitForStart("wait/1", b);
                assertTrue(b.isBuilding());
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                assertThatWorkflowIsSuspended();
                SemaphoreStep.success("wait/1", null);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
            }
        });
    }

    @Ignore("TODO java.io.NotSerializableException: java.util.ArrayList$Itr")
    @Issue("JENKINS-27421")
    @Test public void listIterator() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                    "def arr = []; arr += 'one'; arr += 'two'\n" +
                    "for (int i = 0; i < arr.size(); i++) {def elt = arr[i]; echo \"running C-style loop on ${elt}\"; semaphore \"C-${elt}\"}\n" +
                    "for (def elt : arr) {echo \"running new-style loop on ${elt}\"; semaphore \"new-${elt}\"}"
                    , true));
                ScriptApproval.get().approveSignature("staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods plus java.util.Collection java.lang.Object"); // TODO ought to be in generic-whitelist
                startBuilding();
                SemaphoreStep.waitForStart("C-one/1", b);
                story.j.waitForMessage("running C-style loop on one", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                SemaphoreStep.success("C-one/1", null);
                SemaphoreStep.success("C-two/1", null);
                story.j.waitForMessage("running C-style loop on two", b);
                SemaphoreStep.waitForStart("new-one/1", b);
                story.j.waitForMessage("running new-style loop on one", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                SemaphoreStep.success("new-one/1", null);
                SemaphoreStep.success("new-two/1", null);
                story.j.waitForCompletion(b);
                story.j.assertBuildStatusSuccess(b);
                story.j.assertLogContains("running new-style loop on two", b);
            }
        });
    }

    @Test public void nonCps() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                    "echo \"first parse: ${parse('foo <version>1.0</version> bar')}\"\n" +
                    "echo \"second parse: ${parse('foo bar')}\"\n" +
                    "@NonCPS def parse(text) {\n" +
                    "  def matcher = text =~ '<version>(.+)</version>'\n" +
                    "  matcher ? matcher[0][1] : null\n" +
                    "}\n", true));
                b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("first parse: 1.0", b);
                story.j.assertLogContains("second parse: null", b);
            }
        });
    }

    @Ignore("TODO JENKINS-31314: calls writeFile just once, echoes null (i.e., return value of writeFile), then succeeds")
    @Test public void nonCpsContinuable() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                ScriptApproval.get().approveSignature("staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods each java.lang.Object groovy.lang.Closure"); // TODO whitelist; should work inside @NonCPS but pending JENKINS-26481 not outside
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                    "@NonCPS def shouldBomb() {\n" +
                    "  def text = ''\n" +
                    "  ['a', 'b', 'c'].each {it -> writeFile file: it, text: it; text += it}\n" +
                    "  text\n" +
                    "}\n" +
                    "node {\n" +
                    "  echo shouldBomb()\n" +
                    "}\n", true));
                b = story.j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
            }
        });
    }

}
