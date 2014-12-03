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
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.support.pickles.SingleTypedPickleFactory;
import org.jenkinsci.plugins.workflow.pickles.Pickle;
import org.jenkinsci.plugins.workflow.support.concurrent.Futures;
import org.jenkinsci.plugins.workflow.test.SemaphoreListener;
import org.jenkinsci.plugins.workflow.test.steps.BlockSemaphoreStep;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import com.google.common.util.concurrent.ListenableFuture;
import hudson.model.Action;
import hudson.model.Queue;
import hudson.util.XStream2;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;

import static org.junit.Assert.*;

public class STMExecutionTest {

    static {
        STMExecution.LOGGER.setLevel(Level.FINE);
        Handler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        STMExecution.LOGGER.addHandler(handler);
    }

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void basics() throws Exception {
        SemaphoreStep step = new SemaphoreStep();
        final AtomicReference<FlowExecution> exec = new AtomicReference<FlowExecution>();
        exec.set(new STMFlowDefinition(Collections.<State>singletonList(new StepState("run", null, step))).create(new OwnerImpl(exec, tmp), Collections.<Action>emptyList()));
        SemaphoreListener l = new SemaphoreListener();
        exec.get().addListener(l);
        exec.get().start();
        FlowNode n = l.next();
        assertTrue(String.valueOf(n), n instanceof FlowStartNode);
        n = l.next();
        assertTrue(n instanceof BlockStartNode);
        step.success(null);
        n = l.next();
        assertTrue(n instanceof AtomNode);
        // TODO check that it is finished now
        n = l.next();
        assertTrue(n instanceof BlockEndNode);
        // TODO check that it matches the BlockStartNode
        n = l.next();
        assertTrue(n instanceof FlowEndNode);
        // TODO check that it matches the FlowStartNode
        // TODO check that its parent list is the BlockEndNode
        assertTrue(exec.get().isComplete());
    }

    @Test public void blocks() throws Throwable {
        SemaphoreStep step = new SemaphoreStep();
        BlockSemaphoreStep block = new BlockSemaphoreStep();
        final AtomicReference<FlowExecution> exec = new AtomicReference<FlowExecution>();
        exec.set(new STMFlowDefinition(Arrays.<State>asList(new BlockState("block", STMExecution.END, block, "step"), new StepState("step", STMExecution.END, step))).create(new OwnerImpl(exec, tmp), Collections.<Action>emptyList()));
        SemaphoreListener l = new SemaphoreListener();
        exec.get().addListener(l);
        exec.get().start();
        FlowNode n = l.next();
        assertTrue(String.valueOf(n), n instanceof FlowStartNode);
        n = l.next();
        assertTrue(n instanceof BlockStartNode);
        n = l.next();
        assertTrue(n instanceof BlockStartNode);
        block.startBlock();
        step.success(null);
        n = l.next();
        assertTrue(n instanceof AtomNode);
        assertEquals(null, block.waitForBlock());
        block.finishSuccess(null);
        n = l.next();
        assertTrue(n instanceof BlockEndNode);
        n = l.next();
        assertTrue(n instanceof BlockEndNode);
        n = l.next();
        assertTrue(n instanceof FlowEndNode);
        assertTrue(exec.get().isComplete());
    }

    @Ignore("TODO")
    @Test public void contextOverridesAndSerialization() throws Throwable {
        SemaphoreStep step = new SemaphoreStep();
        BlockSemaphoreStep block = new BlockSemaphoreStep();
        final AtomicReference<FlowExecution> exec = new AtomicReference<FlowExecution>();
        exec.set(new STMFlowDefinition(Arrays.<State>asList(new BlockState("block", STMExecution.END, block, "step"), new StepState("step", STMExecution.END, step))).create(new OwnerImpl(exec, tmp), Collections.<Action>emptyList()));
        exec.get().start();
        block.startBlock(new Thing(17));
        Thing t = step.getContext().get(Thing.class);
        assertNotNull(t);
        assertEquals(17, t.number);
        exec.set(reserialize(exec.get()));
        step = reserialize(step);
        block = reserialize(block);
        t = step.getContext().get(Thing.class);
        assertNotNull(t);
        assertEquals(17, t.number);
        step.success(null);
        assertEquals(null, block.waitForBlock());
        block.finishSuccess(null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T reserialize(T object) {
        XStream2 xs = new XStream2();
        String xml = xs.toXML(object);
        System.out.println(xml);
        return (T) xs.fromXML(xml);
    }

    private static class Thing {
        final int number;
        Thing(int number) {
            this.number = number;
        }
        private Object writeReplace() {
            throw new AssertionError("not directly serializable");
        }
    }
    private static class ThingPickle extends Pickle {
        final int number;
        ThingPickle(Thing t) {
            number = t.number;
        }
        @Override public ListenableFuture<Thing> rehydrate() {
            return Futures.immediateFuture(new Thing(number));
        }
    }
    static {
        STMExecution.valueFactories = Collections.singleton(new SingleTypedPickleFactory<Thing>() {
            @Override protected Pickle pickle(Thing thing) {
                return new ThingPickle(thing);
            }
        });
    }

    private static class OwnerImpl extends FlowExecutionOwner {
        private static final List<AtomicReference<FlowExecution>> execs = new ArrayList<AtomicReference<FlowExecution>>();
        private final int which;
        private final File rootDir;
        OwnerImpl(AtomicReference<FlowExecution> exec,TemporaryFolder tmp) {
            which = execs.size();
            execs.add(exec);
            rootDir = tmp.getRoot();
        }
        @Override public FlowExecution get() throws IOException {
            return execs.get(which).get();
        }
        @Override public File getRootDir() throws IOException {
            return rootDir;
        }
        @Override public Queue.Executable getExecutable() throws IOException {
            throw new IOException();
        }
        @Override public String getUrl() {
            return "TODO";
        }

        @Override
        public boolean equals(Object o) {
            return this==o;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

}