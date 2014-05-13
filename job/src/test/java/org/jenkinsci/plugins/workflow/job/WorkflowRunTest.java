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

package org.jenkinsci.plugins.workflow.job;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.test.steps.WatchYourStep;
import hudson.FilePath;
import hudson.model.BallColor;
import hudson.model.queue.QueueTaskFuture;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import javax.inject.Inject;
import java.io.IOException;

import static org.junit.Assert.*;

public class WorkflowRunTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    WorkflowJob p;

    @Inject
    WatchYourStep.DescriptorImpl watch;

    @Before
    public void setUp() throws Exception {
        p = r.jenkins.createProject(WorkflowJob.class, "p");
        r.jenkins.getInjector().injectMembers(this);
    }

    @Test public void basics() throws Exception {
        p.setDefinition(new CpsFlowDefinition("println('hello')"));
        WorkflowRun b1 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertFalse(b1.isBuilding());
        assertFalse(b1.isInProgress());
        assertFalse(b1.isLogUpdated());
        WorkflowRun b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(b1, b2.getPreviousBuild());
        assertEquals(null, b1.getPreviousBuild());
    }

    /**
     * Verifies that {@link WorkflowRun#getIconColor()} returns the right color.
     */
    @Test
    public void iconColor() throws Exception {
        // marker file I use for synchronization
        FilePath test = new FilePath(r.jenkins.root).child("touch");


        p.setDefinition(new CpsFlowDefinition(
            "println('hello')\n"+
            "steps.watch(new File('"+test.getRemote()+"'))\n"+
            "println('hello')\n"
        ));

        // no build exists yet
        assertSame(p.getIconColor(),BallColor.NOTBUILT);

        // get a build going
        QueueTaskFuture<WorkflowRun> q = p.scheduleBuild2(0);
        WorkflowRun b1 = q.getStartCondition().get();
        CpsFlowExecution e = (CpsFlowExecution) b1.getExecutionPromise().get();

        // initial state should be blinking gray
        assertFalse(b1.hasntStartedYet());
        assertColor(b1, BallColor.NOTBUILT_ANIME);

        e.waitForSuspension();

        // at the pause point, it should be still blinking gray
        assertFalse(b1.hasntStartedYet());
        assertColor(b1, BallColor.NOTBUILT_ANIME);

        test.touch(0);
        watch.watchUpdate();

        // bring it to the completion
        e.waitForSuspension();
        assertTrue(e.isComplete());

        // and the color should be now solid blue
        assertFalse(b1.hasntStartedYet());
        assertColor(b1, BallColor.BLUE);

        // get another one going
        test.delete();
        q = p.scheduleBuild2(0);
        WorkflowRun b2 = q.getStartCondition().get();
        e = (CpsFlowExecution) b2.getExecutionPromise().get();

        // initial state should be blinking blue because the last one was blue
        assertFalse(b2.hasntStartedYet());
        assertColor(b2, BallColor.BLUE_ANIME);

        e.waitForSuspension();

        // at the pause point, it should be still blinking gray
        assertFalse(b2.hasntStartedYet());
        assertColor(b2, BallColor.BLUE_ANIME);

        // bring it to the completion
        test.touch(0);
        watch.watchUpdate();
        e.waitForSuspension();

        // and the color should be now solid blue
        assertFalse(b2.hasntStartedYet());
        assertColor(b2, BallColor.BLUE);
    }

    private void assertColor(WorkflowRun b, BallColor color) throws IOException {
        assertSame(b.getLog(), color, b.getIconColor());
        assertSame(b.getLog(), color, p.getIconColor());
    }
}
