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
import org.jenkinsci.plugins.workflow.steps.StepContext;
import com.google.common.util.concurrent.FutureCallback;
import hudson.model.Result;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

final class STMContext extends StepContext {

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

    private STMExecution execution() throws IOException {
        FlowExecution exec = handle.get();
        if (exec instanceof STMExecution) {
            return (STMExecution) exec;
        } else {
            throw new IOException("expected an STMExecution but got a " + exec);
        }
    }

    @Override public <T> T get(Class<T> key) throws IOException, InterruptedException {
        return null; // TODO
    }

    @Override public boolean isReady() throws IOException, InterruptedException {
        return true; // TODO
    }

    @Override public Object getGlobalVariable(String name) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override public void setGlobalVariable(String name, Object v) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override public void setResult(Result r) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override public void invokeBodyLater(FutureCallback callback, Object... contextOverrides) {
        STMExecution exec;
        try {
            exec = execution();
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
            execution().success(id, returnValue);
        } catch (IOException x) {
            LOG.log(Level.WARNING, null, x);
        }
    }

}
