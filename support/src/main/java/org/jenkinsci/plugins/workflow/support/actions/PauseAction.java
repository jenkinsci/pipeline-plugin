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

import hudson.model.Action;
import hudson.model.InvisibleAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Pause {@link FlowNode} Action.
 * Simply marks the node as being a node that causes the build to pause e.g. an Input node.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class PauseAction extends InvisibleAction {

    private static final Logger LOGGER = Logger.getLogger(PauseAction.class.getName());

    private String cause;
    private long startTime = System.currentTimeMillis();
    private long endTime;

    public PauseAction(String cause) {
        this.cause = cause;
    }

    public String getCause() {
        return cause;
    }

    public void setCause(String cause) {
        this.cause = cause;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public boolean isPaused() {
        // The node is paused if the end time is not set on it.
        return (endTime == 0L);
    }

    /**
     * Get the pause duration for this flow node.
     * If the node is paused, the duration will be calculated against the current time.
     *
     * @return The pause duration in milliseconds.
     */
    public long getPauseDuration() {
        if (isPaused()) {
            return (System.currentTimeMillis() - startTime);
        } else {
            return (endTime - startTime);
        }
    }

    public static @CheckForNull PauseAction getCurrentPause(@Nonnull FlowNode node) {
        List<PauseAction> pauseActions = getPauseActions(node);

        if (!pauseActions.isEmpty()) {
            return pauseActions.get(pauseActions.size() - 1);
        }

        return null;
    }

    public static void endCurrentPause(@Nonnull FlowNode node) throws IOException {
        PauseAction currentPause = getCurrentPause(node);

        if (currentPause != null) {
            currentPause.setEndTime(System.currentTimeMillis());
            node.save();
        } else {
            LOGGER.log(Level.FINE, "‘endCurrentPause’ was called for a FlowNode (‘{0}’) that does not have an active pause. ‘endCurrentPause’ may have already been called.", node.getDisplayName());
        }
    }

    /**
     * Simple helper method to test if the supplied node is a pause node.
     * @param node The node to test.
     * @return True if the node is pause node, otherwise false.
     */
    public static boolean isPaused(@Nonnull FlowNode node) {
        PauseAction currentPause = getCurrentPause(node);

        if (currentPause != null) {
            return currentPause.isPaused();
        }

        return false;
    }

    /**
     * Get the {@link PauseAction} instances for the supplied node.
     * @param node The node to be searched.
     * @return The {@link PauseAction} instances for the supplied node. Returns an empty list if there are none.
     */
    public static @Nonnull List<PauseAction> getPauseActions(@Nonnull FlowNode node) {
        List<PauseAction> pauseActions = new ArrayList<PauseAction>();
        List<Action> actions = node.getActions();

        for (Action action : actions) {
            if (action instanceof PauseAction) {
                pauseActions.add((PauseAction) action);
            }
        }

        return pauseActions;
    }

    /**
     * get the aggregate pause duration of the supplied flow node.
     * @param node The node to calculate on.
     * @return The pause duration in milliseconds.
     */
    public static long getPauseDuration(@Nonnull FlowNode node) {
        List<PauseAction> pauseActions = getPauseActions(node);
        long pauseDuration = 0L;

        for (PauseAction pauseAction : pauseActions) {
            pauseDuration += pauseAction.getPauseDuration();
        }

        return pauseDuration;
    }
}
