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

package org.jenkinsci.plugins.workflow.test.steps;

import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import com.google.common.util.concurrent.FutureCallback;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import java.util.Map;
import java.util.Set;

/**
 * Block step that can be externally controlled.
 */
public final class BlockSemaphoreStep extends Step {

    public enum State {
        /** {@link #start} has not yet been called. */
        INIT,
        /** {@link #start} has been called, but the block has not started. */
        STARTED,
        /** {@link StepContext#newBodyInvoker} has been called, so the block has started. */
        BLOCK_STARTED,
        /** {@link FutureCallback} from {@link StepContext#newBodyInvoker} has been notified, so the block has ended. */
        BLOCK_ENDED,
        /** {@link FutureCallback} from {@link Step} has been notified, so the whole step has ended. */
        DONE,
        /** Aborted through {@link StepExecution#stop(FlowInterruptedException)}. */
        STOPPED
    }

    private State state = State.INIT;
    private StepContext context;
    private Object blockReturnValue;
    private Throwable blockFailure;

    private void moveFrom(State startingState) {
        assert state == startingState : state;
        state = State.values()[state.ordinal() + 1];
    }

    public State getState() {
        return state;
    }

    @Override public StepExecution start(final StepContext context) throws Exception {
        this.context = context;
        return new StepExecution(context) {
            @Override
            public boolean start() throws Exception {
                moveFrom(State.INIT);
                return false;
            }

            @Override
            public void stop(Throwable cause) throws Exception {
                state = State.STOPPED; // force the state change regardless of the current state
                context.onFailure(cause);
            }
        };
    }

    public void startBlock(Object... contextOverrides) {
        moveFrom(State.STARTED);
        context.newBodyInvoker().withContext(contextOverrides).withCallback(new Callback()).start();
    }
    
    private class Callback implements FutureCallback {
        @Override public void onSuccess(Object returnValue) {
            blockReturnValue = returnValue;
            blockDone();
        }
        @Override public void onFailure(Throwable t) {
            blockFailure = t;
            blockDone();
        }
    }

    private synchronized void blockDone() {
        moveFrom(State.BLOCK_STARTED);
        notifyAll();
    }

    public synchronized Object waitForBlock() throws Throwable {
        while (state == State.BLOCK_STARTED) {
            wait();
        }
        assert state == State.BLOCK_ENDED : state;
        if (blockFailure != null) {
            throw blockFailure;
        } else {
            return blockReturnValue;
        }
    }

    public void finishSuccess(Object returnValue) {
        moveFrom(State.BLOCK_ENDED);
        context.onSuccess(returnValue);
    }

    public void finishFailure(Throwable t) {
        moveFrom(State.BLOCK_ENDED);
        context.onFailure(t);
    }

    @Override public StepDescriptor getDescriptor() {
        return new DescriptorImpl();
    }

    /* not an @Extension */ private static final class DescriptorImpl extends StepDescriptor {

        @Override public Set<Class<?>> getRequiredContext() {
            throw new UnsupportedOperationException();
        }

        @Override public String getFunctionName() {
            throw new UnsupportedOperationException();
        }

        @Override public Step newInstance(Map<String,Object> arguments) {
            throw new UnsupportedOperationException();
        }

        @Override public Map<String,Object> defineArguments(Step step) throws UnsupportedOperationException {
            throw new UnsupportedOperationException();
        }

        @Override public String getDisplayName() {
            return "Test block step";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

    }

}
