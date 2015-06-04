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
import org.jenkinsci.plugins.workflow.support.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import hudson.Extension;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.OneOffExecutor;
import hudson.model.Queue;
import hudson.model.queue.SubTask;

import java.util.concurrent.Future;

/**
 * Persists an {@link Executor} as the {@link hudson.model.Queue.Task} it was running.
 * That task can in turn have some way of producing a display name, a special {@link hudson.model.Queue.Executable} with a custom {@code executorCell.jelly}, and so on.
 * When rehydrated, the task is rescheduled, and when it starts executing the owning executor is produced.
 * Typically the {@link SubTask#getAssignedLabel} should be a {@link Node#getSelfLabel} so that the rehydrated executor is in fact on the same node.
 */
public class ExecutorPickle extends Pickle {

    private final Queue.Task task;

    private ExecutorPickle(Executor executor) {
        if (executor instanceof OneOffExecutor) {
            throw new IllegalArgumentException("OneOffExecutor not currently supported");
        }
        Queue.Executable exec = executor.getCurrentExecutable();
        if (exec == null) {
            throw new IllegalArgumentException("cannot save an Executor that is not running anything");
        }
        SubTask parent = exec.getParent();
        this.task = parent instanceof Queue.Task ? (Queue.Task) parent : parent.getOwnerTask();
        if (task instanceof Queue.TransientTask) {
            throw new IllegalArgumentException("cannot save a TransientTask");
        }
    }

    @Override public ListenableFuture<Executor> rehydrate() {
        Queue.Item item = Queue.getInstance().schedule2(task, 0).getItem();
        if (item == null) {
            // TODO should also report when !ScheduleResult.created, since that is arguably an error
            return Futures.immediateFailedFuture(new IllegalStateException("queue refused " + task));
        }

        final Future<Queue.Executable> future = item.getFuture().getStartCondition();

        return new TryRepeatedly<Executor>(1) {
            @Override
            protected Executor tryResolve() throws Exception {
                if (!future.isDone()) {
                    return null;
                }

                Queue.Executable exec = future.get();
                Executor e = Executor.of(exec);
                if (e != null) {
                    return e;
                }

                // TODO this could happen as a race condition if the executable takes <1s to run; how could that be prevented?
                // Or can we schedule a placeholder Task whose Executable does nothing but return Executor.currentExecutor and then end?
                throw new IllegalStateException(exec + " was scheduled but no executor claimed it");
            }
        };
    }

    @Extension public static final class Factory extends SingleTypedPickleFactory<Executor> {
        @Override protected Pickle pickle(Executor object) {
            return new ExecutorPickle(object);
        }
    }

}
