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

package org.jenkinsci.plugins.workflow;

import hudson.EnvVars;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.slaves.CommandLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import java.io.File;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.HudsonHomeLoader;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

/**
 * Utilities that could be added to {@link JenkinsRule} in the future but are not yet available in our baseline version.
 */
public class JenkinsRuleExt {

    /**
     * Akin to {@link JenkinsRule#createSlave(String, String, EnvVars)} but allows {@link Computer#getEnvironment} to be controlled rather than directly modifying launchers.
     * @param env variables to override in {@link Computer#getEnvironment}; null values will get unset even if defined in the test environment
     * @see <a href="https://github.com/jenkinsci/jenkins/pull/1553/files#r23784822">explanation in core PR 1553</a>
     */
    public static Slave createSpecialEnvSlave(JenkinsRule rule, String nodeName, @CheckForNull String labels, Map<String,String> env) throws Exception {
        @SuppressWarnings("deprecation") // keep consistency with original signature rather than force the caller to pass in a TemporaryFolder rule
        File remoteFS = rule.createTmpDir();
        SpecialEnvSlave slave = new SpecialEnvSlave(remoteFS, rule.createComputerLauncher(/* yes null */null), nodeName, labels != null ? labels : "", env);
        rule.jenkins.addNode(slave);
        return slave;
    }
    private static class SpecialEnvSlave extends Slave {
        private final Map<String,String> env;
        SpecialEnvSlave(File remoteFS, CommandLauncher launcher, String nodeName, @Nonnull String labels, Map<String,String> env) throws Descriptor.FormException, IOException {
            super(nodeName, nodeName, remoteFS.getAbsolutePath(), 1, Mode.NORMAL, labels, launcher, RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList());
            this.env = env;
        }
        @Override public Computer createComputer() {
            return new SpecialEnvComputer(this, env);
        }
    }
    private static class SpecialEnvComputer extends SlaveComputer {
        private final Map<String,String> env;
        SpecialEnvComputer(SpecialEnvSlave slave, Map<String,String> env) {
            super(slave);
            this.env = env;
        }
        @Override public EnvVars getEnvironment() throws IOException, InterruptedException {
            EnvVars env2 = super.getEnvironment();
            env2.overrideAll(env);
            return env2;
        }
    }

    /**
     * Prints a stack trace whenever {@link Thread#interrupt} is called on a thread running {@link JenkinsRule#before}.
     */
    private static void diagnoseJenkins30395SetUp() {
        System.setSecurityManager(new SecurityManager() {
            @Override public void checkAccess(Thread t) {
                StackTraceElement[] target = t.getStackTrace();
                if (matches(target, JenkinsRule.class, "before")) {
                    Throwable x = new Throwable("calling Thread.interrupt here");
                    if (matches(x.getStackTrace(), Thread.class, "interrupt")) {
                        x.printStackTrace();
                        System.getProperties().put("diagnoseJenkins30395", x);
                        System.err.println("Target thread:");
                        for (StackTraceElement line : target) {
                            System.err.println("\tat " + line);
                        }
                    }
                }
            }
            boolean matches(StackTraceElement[] stack, Class<?> clazz, String method) {
                String n = clazz.getName();
                for (StackTraceElement line : stack) {
                    if (line.getClassName().equals(n) && line.getMethodName().equals(method)) {
                        return true;
                    }
                }
                return false;
            }
            @Override public void checkPermission(Permission perm) {}
            @Override public void checkPermission(Permission perm, Object context) {}
        });
    }
    public static JenkinsRule diagnoseJenkins30395() {
        diagnoseJenkins30395SetUp();
        return new JenkinsRule() {
            @Override public void before() throws Throwable {
                if (Thread.interrupted()) {
                    InterruptedException x = new InterruptedException("was interrupted before start");
                    Object o = System.getProperties().get("diagnoseJenkins30395");
                    if (o instanceof Throwable) {
                        x.initCause((Throwable) o);
                    }
                    throw x;
                }
                try {
                    super.before();
                } catch (ClosedByInterruptException x) {
                    Object o = System.getProperties().get("diagnoseJenkins30395");
                    if (o instanceof Throwable) {
                        x.initCause((Throwable) o);
                    }
                    throw x;
                }
            }
        };
    }
    public static RestartableJenkinsRule diagnoseJenkins30395Restartable() {
        diagnoseJenkins30395SetUp();
        return new RestartableJenkinsRule() {
            private Description description;
            private final List<Statement> steps = new ArrayList<Statement>();
            private TemporaryFolder tmp = new TemporaryFolder();
            private Object target;
            @Override
            public Statement apply(final Statement base, FrameworkMethod method, Object target) {
                this.description = Description.createTestDescription(
                        method.getMethod().getDeclaringClass(), method.getName(), method.getAnnotations());
                this.target = target;
                return tmp.apply(new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        home = tmp.newFolder();
                        base.evaluate();
                        run();
                    }
                }, description);
            }
            public void addStep(final Statement step) {
                steps.add(new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        j.jenkins.getInjector().injectMembers(step);
                        j.jenkins.getInjector().injectMembers(target);
                        step.evaluate();
                    }
                });
            }
            private void run() throws Throwable {
                HudsonHomeLoader loader = new HudsonHomeLoader() {
                    @Override
                    public File allocate() throws Exception {
                        return home;
                    }
                };
                for (Statement step : steps) {
                    j = new JenkinsRule().with(loader);
                    if (Thread.interrupted()) {
                        InterruptedException x = new InterruptedException("was interrupted before start");
                        Object o = System.getProperties().get("diagnoseJenkins30395");
                        if (o instanceof Throwable) {
                            x.initCause((Throwable) o);
                        }
                        throw x;
                    }
                    try {
                        j.apply(step,description).evaluate();
                    } catch (ClosedByInterruptException x) {
                        Object o = System.getProperties().get("diagnoseJenkins30395");
                        if (o instanceof Throwable) {
                            x.initCause((Throwable) o);
                        }
                        throw x;
                    }
                }
            }
        };
    }

    private JenkinsRuleExt() {}

}
