/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.flow;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.org.apache.tools.tar.TarInputStream;
import hudson.util.DirScanner;
import hudson.util.io.ArchiverFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.ArtifactManager;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tools.tar.TarEntry;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * Manages per-build stashes of files.
 * Unlike artifacts managed by {@link ArtifactManager}, stashes:
 * <ul>
 * <li>Are expected to be transferred to other workspaces during the build.
 * <li>Generally are discarded when the build finishes.
 * <li>Are not exposed as part of the build outside Jenkins, for example via REST.
 * <li>Are stored in an archive format with a simple name, not necessarily related to filenames.
 * </ul>
 */
public class StashManager {

    /**
     * Saves a stash of some files from a build.
     * @param build a build to use as storage
     * @param name a simple name to assign to the stash (must follow {@link Jenkins#checkGoodName} constraints)
     * @param workspace a directory to use as a base
     * @param includes a set of Ant-style file includes, separated by commas; null/blank is allowed as a synonym for {@code **} (i.e., everything)
     * @param excludes an optional set of Ant-style file excludes
     */
    @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", justification="fine if mkdirs returns false")
    public static void stash(@Nonnull Run<?,?> build, @Nonnull String name, @Nonnull FilePath workspace, @Nonnull TaskListener listener, @CheckForNull String includes, @CheckForNull String excludes) throws IOException, InterruptedException {
        Jenkins.checkGoodName(name);
        File storage = storage(build, name);
        storage.getParentFile().mkdirs();
        if (storage.isFile()) {
            listener.getLogger().println("Warning: overwriting stash ‘" + name + "’");
        }
        OutputStream os = new FileOutputStream(storage);
        try {
            int count = workspace.archive(ArchiverFactory.TARGZ, os, new DirScanner.Glob(Util.fixEmpty(includes) == null ? "**" : includes, excludes));
            if (count == 0) {
                throw new AbortException("No files included in stash");
            }
            listener.getLogger().println("Stashed " + count + " file(s)");
        } finally {
            os.close();
        }
    }

    /**
     * Restores a stash of some files from a build.
     * @param build a build used as storage
     * @param name a name passed previously to {@link #stash}
     * @param workspace a directory to copy into
     */
    public static void unstash(@Nonnull Run<?,?> build, @Nonnull String name, @Nonnull FilePath workspace, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        Jenkins.checkGoodName(name);
        File storage = storage(build, name);
        if (!storage.isFile()) {
            throw new AbortException("No such saved stash ‘" + name + "’");
        }
        InputStream is = new FileInputStream(storage);
        try {
            workspace.untarFrom(is, FilePath.TarCompression.GZIP);
            // currently nothing to print; listener is a placeholder
        } finally {
            is.close();
        }
    }

    /**
     * Delete any and all stashes in a build.
     * @param build a build possibly passed to {@link #stash} in the past
     */
    public static void clearAll(@Nonnull Run<?,?> build) throws IOException {
        Util.deleteRecursive(storage(build));
    }

    /**
     * Delete any and all stashes in a build unless told otherwise.
     * {@link StashBehavior#shouldClearAll} may cancel this.
     * @param build a build possibly passed to {@link #stash} in the past
     */
    public static void maybeClearAll(@Nonnull Run<?,?> build) throws IOException {
        for (StashBehavior behavior : ExtensionList.lookup(StashBehavior.class)) {
            if (!behavior.shouldClearAll(build)) {
                return;
            }
        }
        clearAll(build);
    }

    /**
     * Copy any stashes from one build to another.
     * @param from a build possibly passed to {@link #stash} in the past
     * @param to a new build
     */
    public static void copyAll(@Nonnull Run<?,?> from, @Nonnull Run<?,?> to) throws IOException {
        File fromStorage = storage(from);
        if (!fromStorage.isDirectory()) {
            return;
        }
        FileUtils.copyDirectory(fromStorage, storage(to));
    }

    @Restricted(DoNotUse.class) // currently just for tests
    @SuppressFBWarnings(value="DM_DEFAULT_ENCODING", justification="test code")
    public static Map<String,Map<String,String>> stashesOf(@Nonnull Run<?,?> build) throws IOException {
        Map<String,Map<String,String>> result = new TreeMap<String,Map<String,String>>();
        File[] kids = storage(build).listFiles();
        if (kids != null) {
            for (File kid : kids) {
                String n = kid.getName();
                if (n.endsWith(SUFFIX)) {
                    Map<String,String> unpacked = new TreeMap<String,String>();
                    result.put(n.substring(0, n.length() - SUFFIX.length()), unpacked);
                    InputStream is = new FileInputStream(kid);
                    try {
                        InputStream wrapped = FilePath.TarCompression.GZIP.extract(is);
                        TarInputStream tis = new TarInputStream(wrapped);
                        TarEntry te;
                        while ((te = tis.getNextEntry()) != null) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            IOUtils.copy(tis, baos);
                            unpacked.put(te.getName(), baos.toString());
                        }
                    } finally {
                        is.close();
                    }
                }
            }
        }
        return result;
    }

    private static @Nonnull File storage(@Nonnull Run<?,?> build) {
        return new File(build.getRootDir(), "stashes");
    }

    private static @Nonnull File storage(@Nonnull Run<?,?> build, @Nonnull String name) {
        File dir = storage(build);
        File f = new File(dir, name + SUFFIX);
        if (!f.getParentFile().equals(dir)) {
            throw new IllegalArgumentException();
        }
        return f;
    }

    private static final String SUFFIX = ".tar.gz";

    private StashManager() {}

    /**
     * Extension point for customizing behavior of stashes from other plugins.
     */
    public static abstract class StashBehavior implements ExtensionPoint {

        /**
         * Allows the normal clearing behavior to be suppressed.
         * @param build a build which has finished
         * @return true (the default) to go ahead and call {@link #clearAll}, false to stop
         */
        public boolean shouldClearAll(@Nonnull Run<?,?> build) {
            return true;
        }

    }

}
