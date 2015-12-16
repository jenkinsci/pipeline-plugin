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
import hudson.LauncherDecorator;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.support.actions.EnvironmentAction;
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
     * <p>{@inheritDoc}
     */
    @edu.umd.cs.findbugs.annotations.SuppressWarnings("OBL_UNSATISFIED_OBLIGATION_EXCEPTION_EDGE") // stream closed later
    @Override public final <T> T get(Class<T> key) throws IOException, InterruptedException {
        T value = doGet(key);
        if (key == EnvVars.class) {
            Run<?,?> run = get(Run.class);
            EnvironmentAction a = run.getAction(EnvironmentAction.class);
            EnvVars customEnvironment = a != null ? a.getEnvironment() : run.getEnvironment(get(TaskListener.class));
            return key.cast(EnvironmentExpander.getEffectiveEnvironment(customEnvironment, (EnvVars) value, get(EnvironmentExpander.class)));
        } else if (key == Launcher.class) {
            return key.cast(makeLauncher((Launcher) value));
        } else if (value != null) {
            return value;
        } else if (key == TaskListener.class) {
            if (listener == null) {
                LogActionImpl la = getNode().getAction(LogActionImpl.class);
                if (la == null) {
                    // TODO: use the default charset of the contextual Computer object
                    la = new LogActionImpl(getNode(), Charset.defaultCharset());
                    getNode().addAction(la);
                }
                ConsoleLogFilter filter = get(ConsoleLogFilter.class);
                OutputStream os = new FileOutputStream(la.getLogFile(), true);
                if (filter != null) {
                    os = filter.decorateLogger((AbstractBuild) null, os);
                }
                listener = new StreamTaskListener(os);
                getExecution().addListener(new GraphListener() {
                    @Override public void onNewHead(FlowNode node) {
                        try {
                            if (!getNode().isRunning()) {
                                listener.getLogger().close();
                            }
                        } catch (IOException x) {
                            Logger.getLogger(DefaultStepContext.class.getName()).log(Level.FINE, null, x);
                        }
                    }
                });
            }
            return key.cast(listener);
        } else if (Node.class.isAssignableFrom(key)) {
            Computer c = get(Computer.class);
            Node n = null;
            if (c != null) {
                n = c.getNode();
            }
            /* contract is to quietly return null:
            if (n == null) {
                throw new IllegalStateException("There is no current node. Perhaps you forgot to call node?");
            }
            */
            return castOrNull(key, n);
        } else if (Run.class.isAssignableFrom(key)) {
            return castOrNull(key, getExecution().getOwner().getExecutable());
        } else if (Job.class.isAssignableFrom(key)) {
            return castOrNull(key, get(Run.class).getParent());
        } else if (FlowExecution.class.isAssignableFrom(key)) {
            return castOrNull(key,getExecution());
        } else {
            // unrecognized key
            return null;
        }
    }

    private <T> T castOrNull(Class<T> key, Object o) {
        if (key.isInstance(o))  return key.cast(o);
        else                    return null;
    }

    private @CheckForNull Launcher makeLauncher(@CheckForNull Launcher contextual) throws IOException, InterruptedException {
        Launcher launcher = contextual;
        Node n = get(Node.class);
        if (launcher == null) {
            if (n == null) {
                return null;
            }
            launcher = n.createLauncher(get(TaskListener.class));
        }
        LauncherDecorator decorator = get(LauncherDecorator.class);
        if (decorator != null && n != null) {
            launcher = decorator.decorate(launcher, n);
        }
        return launcher;
    }

    /**
     * The actual logic of {@link #get}, such as retrieving overrides passed to {@link #newBodyInvoker}.
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
