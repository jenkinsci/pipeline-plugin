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
 * <p>
 * {@link #eval(CpsThread)} can return in one of two ways.
 *
 * <p>
 * If the eval method returns with
 * {@link ThreadTaskResult#resumeWith(Outcome)}, then the calling CPS program sees
 * the invocation of {@link Continuable#suspend(Object)} "return"
 * (in the async sense) with the specified outcome. This is useful if you want to
 * keep the CPS evaluation going.
 *
 * <p>
 * If the method return with {@link ThreadTaskResult#suspendWith(Outcome)}, then
 * the synchronous caller of {@link Continuable#run(Object)} returns with the specified
 * outcome (which is normally what happens with {@link Continuable#suspend(Object)}) call.
 * This is useful if you want to suspend the computation.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ThreadTask {
    /**
     * @param cur
     *      the current thread that requested this task.
     */
    protected abstract ThreadTaskResult eval(CpsThread cur);
}
