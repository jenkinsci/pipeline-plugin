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
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.slaves.ComputerListener;
import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * @author Kohsuke Kawaguchi
 */
public class FilePathPickle extends Pickle {
    private final String slave;
    private final String path;

    private FilePathPickle(FilePath v) {
        // TODO JENKINS-26096 switch to FilePath.toComputer once it uses something like Listener here and so is actually reliable
        slave = Listener.channelNames.get(v.getChannel());
        if (slave == null) {
            throw new IllegalStateException("no known slave for " + v);
        }
        path = v.getRemote();
    }

    @Override
    public ListenableFuture<FilePath> rehydrate() {
        return new TryRepeatedly<FilePath>(1) {
            @Override
            protected FilePath tryResolve() {
                Jenkins j = Jenkins.getInstance();
                if (j == null) {
                    return null;
                }
                Computer c = j.getComputer(slave);
                if (c == null) {
                    return null;
                }
                VirtualChannel ch = c.getChannel();
                if (ch == null) return null;
                return new FilePath(ch, path);
            }
        };
    }

    @Extension public static final class Factory extends SingleTypedPickleFactory<FilePath> {
        @Override protected Pickle pickle(FilePath object) {
            return new FilePathPickle(object);
        }
    }

    @Extension public static final class Listener extends ComputerListener {
        // TODO better to use a synchronized accessor
        @Restricted(NoExternalUse.class)
        public static final Map<VirtualChannel,String> channelNames = new WeakHashMap<VirtualChannel,String>();
        @Override public void onOnline(Computer c, TaskListener l) { // TODO currently preOnline is not called for MasterComputer
            if (c instanceof Jenkins.MasterComputer) {
                channelNames.put(c.getChannel(), c.getName());
            }
        }
        @Override public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener) throws IOException, InterruptedException {
            channelNames.put(channel, c.getName());
        }
    }

}
