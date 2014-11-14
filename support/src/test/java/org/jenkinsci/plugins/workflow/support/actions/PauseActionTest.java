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
package org.jenkinsci.plugins.workflow.support.actions;

import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class PauseActionTest {

    @Test
    public void test() throws Exception {
        FlowExecution flowExecution = Mockito.mock(FlowExecution.class);
        FlowNode flowNode = new FlowNode(flowExecution, "1") {
            @Override
            protected String getTypeDisplayName() {
                return "flownode";
            }
        };

        Assert.assertEquals(false, PauseAction.isPaused(flowNode));
        Assert.assertEquals(0L, PauseAction.getPauseDuration(flowNode));

        flowNode.addAction(new PauseAction("P1"));
        PauseAction firstPause = PauseAction.getCurrentPause(flowNode);
        Assert.assertEquals("P1", firstPause.getCause());
        Assert.assertEquals(true, PauseAction.isPaused(flowNode));
        Thread.sleep(200);
        Assert.assertTrue(PauseAction.getPauseDuration(flowNode) > 100L);

        PauseAction.endCurrentPause(flowNode);
        Assert.assertEquals(false, PauseAction.isPaused(flowNode));
        long firstPauseDuration = firstPause.getPauseDuration();

        Thread.sleep(200);
        Assert.assertEquals(firstPauseDuration, PauseAction.getPauseDuration(flowNode));

        flowNode.addAction(new PauseAction("P2"));
        PauseAction secondPause = PauseAction.getCurrentPause(flowNode);
        Assert.assertEquals("P2", secondPause.getCause());
        Assert.assertEquals(true, PauseAction.isPaused(flowNode));
        Thread.sleep(200);
        Assert.assertTrue(PauseAction.getPauseDuration(flowNode) > firstPauseDuration);

        PauseAction.endCurrentPause(flowNode);
        Assert.assertEquals(false, PauseAction.isPaused(flowNode));
        long secondPauseDuration = secondPause.getPauseDuration();

        Thread.sleep(200);
        Assert.assertEquals((firstPauseDuration + secondPauseDuration), PauseAction.getPauseDuration(flowNode));
    }
}
