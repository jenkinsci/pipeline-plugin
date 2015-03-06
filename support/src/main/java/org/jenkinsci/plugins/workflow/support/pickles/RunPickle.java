/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.pickles.Pickle;

/**
 * Represents a {@link Run}, or null if it has since been deleted.
 */
public class RunPickle extends Pickle {

    private static final long serialVersionUID = 1L;

    private final String externalizableId;

    private RunPickle(Run<?,?> run) {
        externalizableId = run.getExternalizableId();
    }

    @Override public ListenableFuture<?> rehydrate() {
        // Need to use TryRepeatedly rather than Futures.immediateFuture to avoid a stack overflow when the current build serialized itself.
        // TODO tried to pass a delay of 0, but this apparently triggers a race condition in 1.580.1 whereby two copies of the build are loaded, thus BuildVarTest.historyAndPickling hang (rewrite in 1.597 might have fixed this).
        return new TryRepeatedly<Run<?,?>>(1) {
            @Override protected Run<?,?> tryResolve() throws Exception {
                try {
                    return Run.fromExternalizableId(externalizableId);
                } catch (IllegalArgumentException x) {
                    // TODO 1.588+ fromExternalizableId will already return null when appropriate if the build has been deleted after restart
                    // (probably preferable to have a null var and produce an NPE somewhere than to break whole flow loading due to this normal condition)
                    return null;
                }
            }
        };
    }

    @Extension public static final class Factory extends SingleTypedPickleFactory<Run<?,?>> {
        @Override protected Pickle pickle(Run<?,?> run) {
            return new RunPickle(run);
        }
    }

}
