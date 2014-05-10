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
import jenkins.model.Jenkins;

/**
 * Reference to {@link Computer}
 *
 * @author Kohsuke Kawaguchi
 */
public class ComputerPickle extends Pickle {
    String slave;

    private ComputerPickle(Computer v) {
        this.slave = v.getName();
    }

    @Override
    public ListenableFuture<Computer> rehydrate() {
        return new TryRepeatedly<Computer>(1) {
            @Override
            protected Computer tryResolve() {
                return Jenkins.getInstance().getComputer(slave);
            }
        };
    }

    @Extension
    public static final class Factory extends SingleTypedPickleFactory<Computer> {
        @Override protected Pickle pickle(Computer computer) {
            return new ComputerPickle(computer);
        }
    }
}
