/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.cps

import com.cloudbees.groovy.cps.ObjectInputStreamWithLoader
import org.jenkinsci.plugins.workflow.flow.FlowExecution
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner
import hudson.XmlFile
import hudson.model.Run
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.jvnet.hudson.test.JenkinsRule

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class AbstractCpsFlowTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder()

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    /**
     * Currently executing flow.
     */
    CpsFlowExecution exec;

    /**
     * Directory to put {@link #exec} in.
     */
    File rootDir;

    @Before
    void setUp() {
        jenkins.jenkins.getInjector().injectMembers(this);
        rootDir = tmp.newFolder();
        TEST = this;
    }

    public <T> T roundtripSerialization(T cx) {
        def baos = new ByteArrayOutputStream()

        new ObjectOutputStream(baos).writeObject(cx);

        def ois = new ObjectInputStreamWithLoader(
                new ByteArrayInputStream(baos.toByteArray()),
                jenkins.jenkins.pluginManager.uberClassLoader)

        return ois.readObject()
    }

    public <T> T roundtripXStream(T cx) {
        def x = new XmlFile(Run.XSTREAM, tmp.newFile());
        x.write(cx);
        return (T)x.read();
    }

    public CpsFlowExecution createExecution(CpsFlowDefinition fdef) {
        exec = fdef.create(new FlowExecutionOwnerImpl());
        return exec;
    }


    public static class FlowExecutionOwnerImpl extends FlowExecutionOwner {
        @Override
        FlowExecution get() {
            return TEST.exec;
        }

        @Override
        File getRootDir() {
            return TEST.rootDir;
        }

        @Override
        hudson.model.Queue.Executable getExecutable() {
            return null
        }

        @Override
        PrintStream getConsole() {
            return System.out;
        }

        @Override
        String getUrl() {
            return "TODO";
        }

        @Override
        boolean equals(Object o) {
            return this==o;
        }

        @Override
        int hashCode() {
            return 0;
        }
    }

    private static AbstractCpsFlowTest TEST;
}
