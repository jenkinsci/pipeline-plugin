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

package org.jenkinsci.plugins.workflow;

import java.io.File;
import jenkins.model.lazy.BuildReference;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.model.Statement;

/**
 * Makes sure that we do not have problems with build loading.
 */
@Ignore("TODO pretty broken today")
public class WorkflowRunOnLoadTest extends SingleJobTestBase {

    /**
     * Forces {@link BuildReference#get} to always be null, so {@link WorkflowRun#onLoad} is called repeatedly.
     */
    @BeforeClass public static void dropBuildRefs() {
        System.setProperty("jenkins.model.lazy.BuildReference.MODE", "none");
    }

    @Test public void reload() throws Exception {
        reloadTest(false);
    }

    @Test public void reloadWithExecutorStep() throws Exception {
        reloadTest(true);
    }

    private void reloadTest(final boolean withExecutorStep) throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                String script = "watch(new File('" + jenkins().getRootDir() + "/touch'))";
                if (withExecutorStep) {
                    script = "node {" + script + "}";
                }
                p.setDefinition(new CpsFlowDefinition(script));
                startBuilding();
                waitForWorkflowToSuspend();
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                FileUtils.write(new File(jenkins().getRootDir(), "touch"), "");
                watchDescriptor.watchUpdate();
                waitForWorkflowToComplete();
                assertBuildCompletedSuccessfully();
            }
        });
    }

}
