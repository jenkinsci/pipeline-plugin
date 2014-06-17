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

package org.jenkinsci.plugins.workflow.support;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.support.actions.LogActionImpl;

/**
 * Partial implementation of step context.
 */
public abstract class DefaultStepContext extends StepContext {

    /**
     * To prevent double instantiation of task listener, once we create it we keep it here.
     */
    private transient TaskListener listener;

    /**
     * Uses {@link #doGet} but automatically translates certain kinds of objects into others.
     * @inheritDoc
     */
    @Override public final <T> T get(Class<T> key) throws IOException, InterruptedException {
        T value = doGet(key);
        if (value != null) {
            return value;
        } else if (key == TaskListener.class) {
            if (listener == null) {
                LogActionImpl la = getNode().getAction(LogActionImpl.class);
                if (la == null) {
                    // TODO: use the default charset of the contextual Computer object
                    la = new LogActionImpl(getNode(), Charset.defaultCharset());
                    getNode().addAction(la);
                }

                listener = new StreamTaskListener(new FileOutputStream(la.getLogFile(), true));
            }
            return key.cast(listener);
        } else if (key == Node.class) {
            Computer c = get(Computer.class);
            Node n = null;
            if (c != null) {
                n = c.getNode();
            }
            /* contract is to quietly return null:
            if (n == null) {
                throw new IllegalStateException("There is no current node. Perhaps you forgot to call with.node?");
            }
            */
            return key.cast(n);
        } else if (key == Run.class) {
            return key.cast(getExecution().getOwner().getExecutable());
        } else if (key == Job.class) {
            return key.cast(get(Run.class).getParent());
        } else if (key == Launcher.class) {
            Node n = get(Node.class);
            if (n == null) {
                return null;
            }
            return key.cast(n.createLauncher(get(TaskListener.class)));
        } else if (key == EnvVars.class) {
            return key.cast(get(Run.class).getEnvironment(get(TaskListener.class)));
        } else if (key == FlowExecution.class) {
            return key.cast(getExecution());
        } else {
            // unrecognized key
            return null;
        }
    }

    /**
     * The actual logic of {@link #get}, such as retrieving overrides passed to {@link #invokeBodyLater}.
     */
    protected abstract @CheckForNull <T> T doGet(Class<T> key) throws IOException, InterruptedException;

    /**
     * Finds the associated execution.
     */
    protected abstract @Nonnull FlowExecution getExecution() throws IOException;

    /**
     * Finds the associated node.
     */
    protected abstract @Nonnull FlowNode getNode() throws IOException;

}
