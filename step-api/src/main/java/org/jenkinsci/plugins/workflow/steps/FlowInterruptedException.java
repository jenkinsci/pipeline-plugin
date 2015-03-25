/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package org.jenkinsci.plugins.workflow.steps;

import hudson.model.Executor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;

/**
 * Special exception that can be thrown out of {@link StepContext#onFailure} to indicate that the flow was aborted from the inside.
 * (This could be caught like any other exception and rethrown or ignored. It only takes effect if thrown all the way up.)
 * No stack trace is printed, and you can control the {@link Result} and {@link CauseOfInterruption}.
 * Analogous to {@link Executor#interrupt(Result, CauseOfInterruption...)} but does not assume we are running inside an executor thread.
 * There is no need to call this from {@link StepExecution#stop} since in that case the execution owner should have set a {@link CauseOfInterruption.UserInterruption} and {@link Result#ABORTED}.
 */
public final class FlowInterruptedException extends InterruptedException {

    private final @Nonnull Result result;
    private final @Nonnull List<CauseOfInterruption> causes;

    /**
     * Creates a new exception.
     * @param result the desired result for the flow, typically {@link Result#ABORTED}
     * @param causes any indications
     */
    public FlowInterruptedException(@Nonnull Result result, @Nonnull CauseOfInterruption... causes) {
        this.result = result;
        this.causes = Arrays.asList(causes);
    }

    public @Nonnull Result getResult() {
        return result;
    }

    public @Nonnull List<CauseOfInterruption> getCauses() {
        return causes;
    }

    /**
     * If a build catches this exception, it should use this method to report it.
     * @param run
     * @param listener
     */
    public void handle(Run<?,?> run, TaskListener listener) {
        run.addAction(new InterruptedBuildAction(causes));
        for (CauseOfInterruption cause : causes) {
            cause.print(listener);
        }
    }

}
