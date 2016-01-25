/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

import hudson.Functions;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ReadWriteFileStepTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void basics() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        boolean win = Functions.isWindows();
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                (win ? "  bat 'echo hello > f1'\n" : "  sh 'echo hello > f1'\n") +
                "  def text = readFile 'f1'\n" +
                "  text = text.toUpperCase()\n" +
                "  writeFile file: 'f2', text: text\n" +
                (win ? "  bat 'type f2'\n" : "  sh 'cat f2'\n") +
                "}"));
        r.assertLogContains("HELLO", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

	@Test
	public void shouldTestFileExistsStep() throws Exception
	{
        final WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  echo \"test.txt - FileExists: ${fileExists('test.txt')}\" \n" +
                "  writeFile file: 'test2.txt', text:'content of file' \n" +
                "  echo \"test2.txt - FileExists: ${fileExists('test2.txt')}\" \n" +
                "}"));

		WorkflowRun run = p.scheduleBuild2(0).get();
		r.assertLogContains("test.txt - FileExists: false", run); 
		r.assertLogContains("test2.txt - FileExists: true", run);
		r.assertBuildStatusSuccess(run);
    }
}
