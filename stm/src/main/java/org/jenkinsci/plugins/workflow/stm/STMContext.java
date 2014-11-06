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

package org.jenkinsci.plugins.workflow.stm;

import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import hudson.model.Result;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.support.DefaultStepContext;
import org.jenkinsci.plugins.workflow.support.concurrent.Futures;

final class STMContext extends DefaultStepContext {

    private static final Logger LOG = Logger.getLogger(STMContext.class.getName());

    /** Reference to the {@link STMExecution} which owns us. */
    private final FlowExecutionOwner handle;

    /** Flow node ID being run. */
    private final String id;

    /** Step being run. */
    private final String step;

    /** Thread being run. */
    private final String thread;

    STMContext(FlowExecutionOwner handle, String id, String step, String thread) {
        this.handle = handle;
        this.id = id;
        // TODO are these really needed? or could they be inferred from id?
        this.step = step;
        this.thread = thread;
    }

    @Override protected STMExecution getExecution() throws IOException {
        FlowExecution exec = handle.get();
        if (exec instanceof STMExecution) {
            return (STMExecution) exec;
        } else {
            throw new IOException("expected an STMExecution but got a " + exec);
        }
    }

    @Override protected FlowNode getNode() throws IOException {
        FlowNode node = getExecution().getNode(id);
        if (node == null) {
            throw new IOException("no such node " + id);
        }
        return node;
    }

    @Override public <T> T doGet(Class<T> key) throws IOException, InterruptedException {
        return null; // TODO
    }

    @Override public boolean isReady() throws IOException, InterruptedException {
        return true; // TODO
    }

    @Override public void setResult(Result r) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override public void invokeBodyLater(FutureCallback<Object> callback, Object... contextOverrides) {
        STMExecution exec;
        try {
            exec = getExecution();
        } catch (IOException x) {
            LOG.log(Level.WARNING, "could not resume block step", x);
            return;
        }
        State state = exec.getStateMap().get(step);
        if (!(state instanceof BlockState)) {
            LOG.log(Level.WARNING, "was not a BlockState: {0}", state);
            return;
        }
        BlockState block = (BlockState) state;
        String start = block.getStart();
        exec.beginBlock(thread, callback);
        exec.next(thread, start);
    }

    @Override public void onFailure(Throwable t) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override public void onSuccess(Object returnValue) {
        try {
            getExecution().success(id, returnValue);
        } catch (IOException x) {
            LOG.log(Level.WARNING, null, x);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        STMContext that = (STMContext) o;

        return handle.equals(that.handle) && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        int result = handle.hashCode();
        result = 31 * result + id.hashCode();
        return result;
    }

    @Override public ListenableFuture<Void> saveState() {
        return Futures.immediateFailedFuture(new UnsupportedOperationException("TODO"));
    }

}
