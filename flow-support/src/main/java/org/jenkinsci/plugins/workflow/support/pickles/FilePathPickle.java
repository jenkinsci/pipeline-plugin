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
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;

/**
 * @author Kohsuke Kawaguchi
 */
public class FilePathPickle extends Pickle {
    String slave;
    String path;

    private FilePathPickle(FilePath v) {
        for (Computer c : Jenkins.getInstance().getComputers()) {
            if (v.getChannel()==c.getChannel()) {
                slave = c.getName();
                path = v.getRemote();
                return;
            }
        }
        throw new IllegalStateException();
    }

    @Override
    public ListenableFuture<FilePath> rehydrate() {
        return new TryRepeatedly<FilePath>(1) {
            @Override
            protected FilePath tryResolve() {
                VirtualChannel ch = Jenkins.getInstance().getComputer(slave).getChannel();
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

}
