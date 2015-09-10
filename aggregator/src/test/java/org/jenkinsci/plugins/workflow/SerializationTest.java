package org.jenkinsci.plugins.workflow;

import hudson.model.Result;
import hudson.slaves.DumbSlave;
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

}
