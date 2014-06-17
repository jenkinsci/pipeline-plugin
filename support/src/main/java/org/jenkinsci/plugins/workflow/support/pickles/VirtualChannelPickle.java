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

import org.jenkinsci.plugins.workflow.pickles.Pickle;
import com.google.common.util.concurrent.ListenableFuture;
import hudson.Extension;
import hudson.model.Computer;
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;

/**
 * Reference to {@link VirtualChannel}
 *
 * @author Kohsuke Kawaguchi
 */
public class VirtualChannelPickle extends Pickle {
    String slave;

    private VirtualChannelPickle(VirtualChannel ch) {
        for (Computer c : Jenkins.getInstance().getComputers()) {
            if (c.getChannel()==ch) {
                slave = c.getName();
                return;
            }
        }
        throw new IllegalArgumentException(ch+" doesn't belong to any slaves");
    }

    @Override
    public ListenableFuture<VirtualChannel> rehydrate() {
        return new TryRepeatedly<VirtualChannel>(1) {
            @Override
            protected VirtualChannel tryResolve() {
                Computer c = Jenkins.getInstance().getComputer(slave);
                if (c==null)    return null;
                return c.getChannel();
            }
        };
    }

    @Extension
    public static final class Factory extends SingleTypedPickleFactory<VirtualChannel> {
        @Override protected Pickle pickle(VirtualChannel object) {
            return new VirtualChannelPickle(object);
        }
    }
}
