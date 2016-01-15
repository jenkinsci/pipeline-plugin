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

package org.jenkinsci.plugins.workflow.job;

import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.FlyweightTask;
import hudson.model.ResourceList;
import hudson.model.queue.AbstractQueueTask;
import hudson.model.queue.CauseOfBlockage;
import java.io.IOException;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Represents a {@link WorkflowRun} still running after a Jenkins restart.
 * Could be a {@code ContinuedTask}, though not really necessary since it is a {@link FlyweightTask} which would never be blocked anyway.
 */
@ExportedBean
class AfterRestartTask extends AbstractQueueTask implements Queue.FlyweightTask, Queue.TransientTask {

    private final WorkflowRun run;

    AfterRestartTask(WorkflowRun run) {
        this.run = run;
    }

    @Override public boolean equals(Object o) {
        return o != null && o.getClass() == getClass() && ((AfterRestartTask) o).run == run;
    }

    @Override public int hashCode() {
        return getClass().hashCode() ^ run.hashCode();
    }

    @Override public boolean isBuildBlocked() {
        return getCauseOfBlockage() != null;
    }

    @Deprecated
    @Override public String getWhyBlocked() {
        CauseOfBlockage causeOfBlockage = getCauseOfBlockage();
        return causeOfBlockage != null ? causeOfBlockage.getShortDescription() : null;
    }

    @Override public String getName() {
        return getDisplayName();
    }

    @Override public String getFullDisplayName() {
        return run.getFullDisplayName();
    }

    @Override public void checkAbortPermission() {
        run.getParent().checkAbortPermission();
    }

    @Override public boolean hasAbortPermission() {
        return run.getParent().hasAbortPermission();
    }

    @Override public String getUrl() {
        return run.getUrl();
    }

    @Override public String getDisplayName() {
        return run.getDisplayName();
    }

    @Override public Label getAssignedLabel() {
        return run.getParent().getAssignedLabel();
    }

    @Override public Node getLastBuiltOn() {
        return run.getParent().getLastBuiltOn();
    }

    @Override public long getEstimatedDuration() {
        return run.getParent().getEstimatedDuration();
    }

    @Override public ResourceList getResourceList() {
        return ResourceList.EMPTY;
    }

    @Override public Queue.Executable createExecutable() throws IOException {
        return run;
    }

    @Override public Authentication getDefaultAuthentication(Queue.Item item) {
        return getDefaultAuthentication();
    }

}
