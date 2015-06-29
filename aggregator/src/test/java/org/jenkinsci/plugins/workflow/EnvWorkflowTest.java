/*
 * The MIT License
 *
 * Copyright (c) 2015 CloudBees, Inc.
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

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Verifies that specific environment variables are available.
 *
 */
public class EnvWorkflowTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    /**
     * Verifies if NODE_NAME environment variable is available on a slave node and on master.
     *
     * @throws Exception
     */
    @Test public void areAvailable() throws Exception {
        r.createSlave("node-test", null, null);
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "workflow-test");

        p.setDefinition(new CpsFlowDefinition(
            "node('master') {\n" +
            "  echo \"My name on master is ${env.NODE_NAME}\"\n" +
            "}\n"
        ));
        r.assertLogContains("My name on master is master", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));

        p.setDefinition(new CpsFlowDefinition(
            "node('node-test') {\n" +
            "  echo \"My name on a slave is ${env.NODE_NAME}\"\n" +
            "}\n"
        ));
        r.assertLogContains("My name on a slave is node-test", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }
}
