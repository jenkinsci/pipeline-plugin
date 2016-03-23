/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
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
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Candidates for inclusion in {@link FilePath}.
 * TODO JENKINS-26096
 */
public class FilePathUtils {

    private static final Logger LOGGER = Logger.getLogger(FilePathUtils.class.getName());

    /**
     * Looks up the {@link Node#getNodeName} corresponding to a given file.
     * Compared to {@link FilePath#toComputer} this has two advantages:
     * <ul>
     * <li>it will still report a configured agent name even if the agent was subsequently disconnected (i.e., the {@link FilePath} is invalid)
     * <li>it will still report a node name even if the agent is connected but currently has no executors
     * </ul>
     * Note that if an administrator disconnects an agent, configures and connects an unrelated agent with the same name,
     * and then this method is called on a path created against the original connection, the result may be misleading.
     * @param f a file, possibly remote
     * @return a node name ({@code ""} for master), if known, else null
     */
    public static @CheckForNull String getNodeNameOrNull(@Nonnull FilePath f) {
        return Listener.getChannelName(f.getChannel());
    }

    /**
     * Same as {@link #getNodeNameOrNull} but throws a diagnostic exception in case of failure.
     * @param f a file, possible remote
     * @return a node name ({@code ""} for master), if known
     * @throws IllegalStateException if the association to a node is unknown
     */
    public static @Nonnull String getNodeName(@Nonnull FilePath f) throws IllegalStateException {
        String name = getNodeNameOrNull(f);
        if (name != null) {
            return name;
        } else {
            throw new IllegalStateException("no known slave for " + f + " among " + Listener.getChannelNames());
        }
    }

    /**
     * Attempts to create a live file handle based on persistable data.
     * @param node a name as returned by {@link #getNodeName}
     * @param path a path as returned by {@link FilePath#getRemote}
     * @return a corresponding file handle, if a node with that name is online, else null
     */
    public static @CheckForNull FilePath find(@Nonnull String node, @Nonnull String path) {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            return null;
        }
        Computer c = j.getComputer(node);
        if (c == null) {
            return null;
        }
        VirtualChannel ch = c.getChannel();
        if (ch == null) {
            return null;
        }
        return new FilePath(ch, path);
    }

    private FilePathUtils() {}

    @Restricted(NoExternalUse.class)
    @Extension public static final class Listener extends ComputerListener {

        private static final Map<VirtualChannel,String> channelNames = new WeakHashMap<>();

        // TODO: sync access?
        static String getChannelName(@Nonnull VirtualChannel channel) {
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

        static Collection<String> getChannelNames() {
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
