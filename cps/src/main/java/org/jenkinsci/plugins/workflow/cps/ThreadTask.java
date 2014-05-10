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

package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Outcome;

/**
 * Execute something in at the point where all CPS threads are at a safe point.
 *
 * <p>
 * Pass this to {@link Continuable#suspend(Object)} to have {@link #eval(CpsThread)}
 * invoked.
 *
 * After the task is executed, {@link Continuable#suspend(Object)} method "returns"
 * (in the async sense) with the value returned.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ThreadTask {
    /**
     * @param t
     *      the thread that requested this task.
     */
    protected abstract Outcome eval(CpsThread t);
}
