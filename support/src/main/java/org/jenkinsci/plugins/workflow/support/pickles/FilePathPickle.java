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
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;

/**
 * @author Kohsuke Kawaguchi
 */
public class FilePathPickle extends Pickle {

    private static final Logger LOGGER = Logger.getLogger(FilePathPickle.class.getName());

    private final String slave;
    private final String path;

    private FilePathPickle(FilePath v) {
        // TODO JENKINS-26096 switch to FilePath.toComputer once it uses something like Listener here and so is actually reliable
        slave = Listener.getChannelName(v.getChannel());
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
        @Restricted(NoExternalUse.class)
        private static final Map<VirtualChannel,String> channelNames = new WeakHashMap<VirtualChannel,String>();

        // TODO: sync access?
        public static String getChannelName(@Nonnull VirtualChannel channel) {
            String channelName = channelNames.get(channel);

            if (channelName == null) {
                // We don't have a cache entry for the name of the Computer associated with the supplied Channel
                // instance. Lets fallback and search the list of Computers on Jenkins.
                Jenkins jenkins = Jenkins.getInstance();
                if (jenkins != null) {
                    for (Computer computer : jenkins.getComputers()) {
                        VirtualChannel computerChannel = computer.getChannel();
                        if (computerChannel != null && computerChannel.equals(channel)) {
                            channelName = computer.getName();
                            addChannel(computerChannel, channelName);
                        }
                    }
                }
            }

            return channelName;
        }

        public static Collection<String> getChannelNames() {
            return channelNames.values();
        }

        @Override public void onOnline(Computer c, TaskListener l) { // TODO currently preOnline is not called for MasterComputer
            if (c instanceof Jenkins.MasterComputer) {
                addChannel(c.getChannel(), c.getName());
            }
        }
        @Override public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener) throws IOException, InterruptedException {
            addChannel(channel, c.getName());
        }

        private static void addChannel(VirtualChannel channel, String computerName) {
            if (channel == null) {
                LOGGER.log(Level.WARNING, "Invalid attempt to add a 'null' Channel instance.");
                return;
            } else if (computerName == null) {
                LOGGER.log(Level.WARNING, "Invalid attempt to add a Channel for a 'null' Computer name.");
                return;
            }
            channelNames.put(channel, computerName);
        }
    }

}
