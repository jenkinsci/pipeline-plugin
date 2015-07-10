/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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
package org.jenkinsci.plugins.workflow.steps.input;

import hudson.model.queue.QueueTaskFuture;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStepExecution;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class InputStepFileParameterTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    
    @Test
    @Issue("JENKINS-29289")
    public void test_input_file_param() throws Exception {
        
        // Job setup:
        // It's a script that looks for an file as input. It:
        //   1. makes sure the file doesn't exist in workspace before the input step is reached
        //   2. manually POSTs a file to the input action to trigger the proceed flow (yeah, could maybe use the webclient etc etc ... too finicky)
        //   3. makes sure the file does exist in workspace after the input step has execute 
        
        WorkflowJob foo = jenkinsRule.jenkins.createProject(WorkflowJob.class, "foo");
        foo.setDefinition(new CpsFlowDefinition("node {\n" +
                "    stage 's1'\n" +
                "    sh 'echo Hi'\n" +
                "\n" +
                "    def file = new File(pwd(), 'text.txt');\n" +
                "\n" +
                "    file.delete();\n" +
                "    if (file.exists()) {\n" +
                "        throw new IllegalStateException(\"File should not exist\");\n" +
                "    } else {\n" +
                "        echo \"${file} is NOT in workspace. OK!!\"\n" +
                "    }\n" +
                "\n" +
                "    input message: 'Load input file', parameters: [[$class: 'FileParameterDefinition', description: '', name: 'text.txt']]\n" +
                "\n" +
                "    // File should now be in the workspace... \n" +
                "\n" +
                "    if (!file.exists()) {\n" +
                "        throw new IllegalStateException(\"File should exist\");\n" +
                "    } else {\n" +
                "        echo \"${file} is in workspace. OK!!\"\n" +
                "    }\n" +
                "\n" +
                "}"));

        // get the build going, and wait until workflow pauses
        QueueTaskFuture<WorkflowRun> q = foo.scheduleBuild2(0);
        WorkflowRun run = q.getStartCondition().get();
        CpsFlowExecution execution = (CpsFlowExecution) run.getExecutionPromise().get();

        while (run.getAction(InputAction.class)==null) {
            execution.waitForSuspension();
        }

        // make sure we are pausing at the right state that reflects what we wrote in the program
        InputAction inputAction = run.getAction(InputAction.class);
        List<InputStepExecution> executions = inputAction.getExecutions();
        Assert.assertEquals(1, executions.size());
        
        String runUrl = getRunUrl(run);
        String inputSubmitUrl = runUrl + "input/" + executions.get(0).getId() + "/submit";
        
        // Manually submit the File parameter
        submitFileParameter("/org/jenkinsci/plugins/workflow/steps/input/hello.txt", inputSubmitUrl);
        
        // Run should complete successfully (no exceptions) ...
        jenkinsRule.assertBuildStatusSuccess(q);
    }

    private String getRunUrl(WorkflowRun run) throws IOException {
        return jenkinsRule.getURL().toString() + run.getUrl();
    }

    private int submitFileParameter(String classpathFile, String endpoint) {
        PostMethod post = new PostMethod(endpoint);

        try {
            File file = toAbsolutePath(classpathFile);            
            Part[] parts = {
                new StringPart("proceed", "Proceed"),
                new StringPart("json", "{\"parameter\": {\"name\": \"text.txt\", \"file\": \"file0\"}}"),
                new FilePart("file0", file)
            };
            
            post.setRequestEntity(new MultipartRequestEntity(parts, post.getParams()));
            HttpClient httpClient =  new HttpClient();
            httpClient.executeMethod(post);

            return post.getStatusCode();
        } catch (Exception e) {
            Assert.fail(e.getMessage());
            return 500; // Keep compiler happy.
        } finally {
            post.releaseConnection();
        }
    }

    private File toAbsolutePath(String classpathFile) {
        return new File(InputStepFileParameterTest.class.getResource(classpathFile).getFile());
    }

}
