/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

package org.jenkinsci.plugins.workflow.steps;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.console.LineTransformationOutputStream;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import jenkins.tasks.SimpleBuildWrapper;
import org.jenkinsci.plugins.workflow.JenkinsRuleExt;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.*;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class CoreWrapperStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test public void useWrapper() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                Assume.assumeFalse(Functions.isWindows()); // TODO create Windows equivalent
                Map<String,String> slaveEnv = new HashMap<String,String>();
                slaveEnv.put("PATH", "/usr/bin:/bin");
                slaveEnv.put("HOME", "/home/jenkins");
                JenkinsRuleExt.createSpecialEnvSlave(story.j, "slave", "", slaveEnv);
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("node('slave') {wrap([$class: 'MockWrapper']) {semaphore 'restarting'; echo \"groovy PATH=${env.PATH}:\"; sh 'echo shell PATH=$PATH:'}}"));
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("restarting/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                SemaphoreStep.success("restarting/1", null);
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                String expected = "/home/jenkins/extra/bin:/usr/bin:/bin";
                story.j.assertLogContains("groovy PATH=" + expected + ":", b);
                story.j.assertLogContains("shell PATH=" + expected + ":", b);
                story.j.assertLogContains("ran DisposerImpl", b);
                story.j.assertLogNotContains("CoreWrapperStep", b);
            }
        });
    }
    public static class MockWrapper extends SimpleBuildWrapper {
        @DataBoundConstructor public MockWrapper() {}
        @Override public void setUp(Context context, Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
            assertNotNull(initialEnvironment.toString(), initialEnvironment.get("PATH"));
            context.env("EXTRA", "${HOME}/extra");
            context.env("PATH+EXTRA", "${EXTRA}/bin");
            context.setDisposer(new DisposerImpl());
        }
        private static final class DisposerImpl extends Disposer {
            private static final long serialVersionUID = 1;
            @Override public void tearDown(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
                listener.getLogger().println("ran DisposerImpl");
            }
        }
        @TestExtension("useWrapper") public static class DescriptorImpl extends BuildWrapperDescriptor {
            @Override public String getDisplayName() {
                return "MockWrapper";
            }
            @Override public boolean isApplicable(AbstractProject<?,?> item) {
                return true;
            }
        }
    }

    @Test public void envStickiness() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                Assume.assumeFalse(Functions.isWindows()); // TODO create Windows equivalent
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "def show(which) {\n" +
                    "  echo \"groovy ${which} ${env.TESTVAR}\"\n" +
                    "  sh \"echo shell ${which} \\$TESTVAR\"\n" +
                    "}\n" +
                    "env.TESTVAR = 'initial'\n" +
                    "node {\n" +
                    "  wrap([$class: 'OneVarWrapper']) {\n" +
                    "    show 'before'\n" +
                    "    env.TESTVAR = 'edited'\n" +
                    "    show 'after'\n" +
                    "  }\n" +
                    "  show 'outside'\n" +
                    "}"));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("received initial", b);
                story.j.assertLogContains("groovy before wrapped", b);
                story.j.assertLogContains("shell before wrapped", b);
                // Any custom values set via EnvActionImpl.setProperty will be “frozen” for the duration of the CoreWrapperStep,
                // because they are always overridden by contextual values.
                story.j.assertLogContains("groovy after wrapped", b);
                story.j.assertLogContains("shell after wrapped", b);
                story.j.assertLogContains("groovy outside edited", b);
                story.j.assertLogContains("shell outside edited", b);
            }
        });
    }
    public static class OneVarWrapper extends SimpleBuildWrapper {
        @DataBoundConstructor public OneVarWrapper() {}
        @Override public void setUp(Context context, Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
            listener.getLogger().println("received " + initialEnvironment.get("TESTVAR"));
            context.env("TESTVAR", "wrapped");
        }
        @TestExtension("envStickiness") public static class DescriptorImpl extends BuildWrapperDescriptor {
            @Override public String getDisplayName() {
                return "OneVarWrapper";
            }
            @Override public boolean isApplicable(AbstractProject<?,?> item) {
                return true;
            }
        }
    }

    @Issue("JENKINS-27392")
    @Test public void loggerDecorator() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("node {echo 'outside #1'; wrap([$class: 'WrapperWithLogger']) {echo 'inside the block'}; echo 'outside #2'}"));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
                story.j.assertLogContains("outside #1", b);
                story.j.assertLogContains("outside #2", b);
                story.j.assertLogContains("INSIDE THE BLOCK", b);
            }
        });
    }
    public static class WrapperWithLogger extends SimpleBuildWrapper {
        @DataBoundConstructor public WrapperWithLogger() {}
        @Override public void setUp(SimpleBuildWrapper.Context context, Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {}
        @Override public ConsoleLogFilter createLoggerDecorator(Run<?,?> build) {
            return new UpcaseFilter();
        }
        private static class UpcaseFilter extends ConsoleLogFilter implements Serializable {
            private static final long serialVersionUID = 1;
            @SuppressWarnings("rawtypes") // inherited
            @Override public OutputStream decorateLogger(AbstractBuild _ignore, final OutputStream logger) throws IOException, InterruptedException {
                return new LineTransformationOutputStream() {
                    @Override protected void eol(byte[] b, int len) throws IOException {
                        logger.write(new String(b, 0, len).toUpperCase(Locale.ROOT).getBytes());
                    }
                };
            }
        }
        @TestExtension("loggerDecorator") public static class DescriptorImpl extends BuildWrapperDescriptor {
            @Override public String getDisplayName() {
                return "WrapperWithLogger";
            }
            @Override public boolean isApplicable(AbstractProject<?,?> item) {
                return true;
            }
        }
    }

}
