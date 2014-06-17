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

package org.jenkinsci.plugins.workflow.support.pickles;

import com.google.common.util.concurrent.ListenableFuture;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.remoting.VirtualChannel;
import hudson.slaves.WorkspaceList;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.pickles.Pickle;

public class WorkspaceListLeasePickle extends Pickle {

    private final String slave;
    private final String path;

    private WorkspaceListLeasePickle(WorkspaceList.Lease lease) {
        Jenkins j = Jenkins.getInstance();
        if (j != null) {
            for (Computer c : j.getComputers()) {
                if (lease.path.getChannel() == c.getChannel()) {
                    slave = c.getName();
                    path = lease.path.getRemote();
                    return;
                }
            }
        }
        throw new IllegalStateException();
    }

    @Override public ListenableFuture<?> rehydrate() {
        return new TryRepeatedly<WorkspaceList.Lease>(1) {
            @Override protected WorkspaceList.Lease tryResolve() throws InterruptedException {
                Jenkins j = Jenkins.getInstance();
                if (j == null) {
                    return null;
                }
                Computer c = j.getComputer(slave);
                if (c == null) {
                    return null;
                }
                VirtualChannel ch = c.getChannel();
                if (ch == null) {
                    return null;
                }
                FilePath fp = new FilePath(ch, path);
                return c.getWorkspaceList().acquire(fp);
            }
        };
    }

    @Extension public static final class Factory extends SingleTypedPickleFactory<WorkspaceList.Lease> {
        @Override protected Pickle pickle(WorkspaceList.Lease lease) {
            return new WorkspaceListLeasePickle(lease);
        }
    }

}
