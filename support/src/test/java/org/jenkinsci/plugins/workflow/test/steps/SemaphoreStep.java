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

import com.google.inject.Inject;
import hudson.Extension;
import hudson.model.Run;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Step that blocks until signaled.
 * Starts running and waits for {@link #success(Object)} or {@link #failure(Throwable)} to be called, if they have not been already.
 */
public final class SemaphoreStep extends AbstractStepImpl implements Serializable {

    /** State of semaphore steps within one Jenkins home and thus (possibly restarting) test. */
    private static final class State {
        private static final Map<File,State> states = new HashMap<File,State>();
        static synchronized State get() {
            File home = Jenkins.getActiveInstance().getRootDir();
            State state = states.get(home);
            if (state == null) {
                state = new State();
                states.put(home, state);
            }
            return state;
        }
        private State() {}
        private final Map<String,Integer> iota = new HashMap<String,Integer>();
        synchronized int allocateNumber(String id) {
            Integer old = iota.get(id);
            if (old == null) {
                old = 0;
            }
            int number = old + 1;
            iota.put(id, number);
            return number;
        }
        /** map from {@link #k} to serial form of {@link StepContext} */
        final Map<String,String> contexts = new HashMap<String,String>();
        final Map<String,Object> returnValues = new HashMap<String,Object>();
        final Map<String,Throwable> errors = new HashMap<String,Throwable>();
        final Set<String> started = new HashSet<String>();
    }

    private final String id;
    private final int number;

    @DataBoundConstructor public SemaphoreStep(String id) {
        this.id = id;
        number = State.get().allocateNumber(id);
    }

    public String getId() {
        return id;
    }

    private String k() {
        return id + "/" + number;
    }

    /** Marks the step as having successfully completed; or, if not yet started, makes it do so synchronously when started. */
    public void success(Object returnValue) {
        success(k(), returnValue);
    }

    public static void success(String k, Object returnValue) {
        State s = State.get();
        if (s.contexts.containsKey(k)) {
            System.err.println("Unblocking " + k + " as success");
            getContext(s, k).onSuccess(returnValue);
        } else {
            System.err.println("Planning to unblock " + k + " as success");
            s.returnValues.put(k, returnValue);
        }
    }

    /** Marks the step as having failed; or, if not yet started, makes it do so synchronously when started. */
    public void failure(Throwable error) {
        failure(k(), error);
    }

    public static void failure(String k, Throwable error) {
        State s = State.get();
        if (s.contexts.containsKey(k)) {
            System.err.println("Unblocking " + k + " as failure");
            getContext(s, k).onFailure(error);
        } else {
            System.err.println("Planning to unblock " + k + " as failure");
            s.errors.put(k, error);
        }
    }
    
    public StepContext getContext() {
        return getContext(State.get(), k());
    }

    private static StepContext getContext(State s, String k) {
        return (StepContext) Jenkins.XSTREAM.fromXML(s.contexts.get(k));
    }

    public static void waitForStart(@Nonnull String k, @CheckForNull Run<?,?> b) throws IOException, InterruptedException {
        State s = State.get();
        synchronized (s) {
            while (!s.started.contains(k)) {
                if (b != null && !b.isBuilding()) {
                    throw new AssertionError(JenkinsRule.getLog(b));
                }
                s.wait(1000);
            }
        }
    }

    public static class Execution extends AbstractStepExecutionImpl {

        @Inject(optional=true) private SemaphoreStep step;

        @Override public boolean start() throws Exception {
            State s = State.get();
            String k = step.k();
            boolean sync = true;
            if (s.returnValues.containsKey(k)) {
                System.err.println("Immediately running " + k);
                getContext().onSuccess(s.returnValues.get(k));
            } else if (s.errors.containsKey(k)) {
                System.err.println("Immediately failing " + k);
                getContext().onFailure(s.errors.get(k));
            } else {
                System.err.println("Blocking " + k);
                s.contexts.put(k, Jenkins.XSTREAM.toXML(getContext()));
                sync = false;
            }
            synchronized (s) {
                s.started.add(k);
                s.notifyAll();
            }
            return sync;
        }

        @Override public void stop(Throwable cause) {
            State s = State.get();
            s.contexts.remove(step.k());
            getContext().onFailure(cause);
        }

        private static final long serialVersionUID = 1L;

    }

    @Extension public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "semaphore";
        }

        @Override public String getDisplayName() {
            return "Test step";
        }

    }

    private static final long serialVersionUID = 1L;
}
