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

import com.google.common.base.Function;
import com.google.inject.Inject;
import hudson.Extension;
import hudson.model.Run;
import java.io.IOException;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Step that blocks until signaled.
 * Starts running and waits for {@link #success(Object)} or {@link #failure(Throwable)} to be called, if they have not been already.
 */
public final class SemaphoreStep extends AbstractStepImpl implements Serializable {

    private static final Map<String,Integer> iota = new HashMap<String,Integer>();
    /** map from {@link #k} to serial form of {@link StepContext}; TODO use {@link StepExecution#applyAll(Class, Function)} instead */
    private static final Map<String,String> contexts = new HashMap<String,String>();
    private static final Map<String,Object> returnValues = new HashMap<String,Object>();
    private static final Map<String,Throwable> errors = new HashMap<String,Throwable>();
    private static final Set<String> started = new HashSet<String>();

    private final String id;
    private final int number;

    @DataBoundConstructor public SemaphoreStep(String id) {
        this.id = id;
        synchronized (iota) {
            Integer old = iota.get(id);
            if (old == null) {
                old = 0;
            }
            number = old + 1;
            iota.put(id, number);
        }
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
        if (contexts.containsKey(k)) {
            System.err.println("Unblocking " + k + " as success");
            getContext(k).onSuccess(returnValue);
        } else {
            System.err.println("Planning to unblock " + k + " as success");
            returnValues.put(k, returnValue);
        }
    }

    /** Marks the step as having failed; or, if not yet started, makes it do so synchronously when started. */
    public void failure(Throwable error) {
        failure(k(), error);
    }

    public static void failure(String k, Throwable error) {
        if (contexts.containsKey(k)) {
            System.err.println("Unblocking " + k + " as failure");
            getContext(k).onFailure(error);
        } else {
            System.err.println("Planning to unblock " + k + " as failure");
            errors.put(k, error);
        }
    }
    
    public StepContext getContext() {
        return getContext(k());
    }

    private static StepContext getContext(String k) {
        return (StepContext) Jenkins.XSTREAM.fromXML(contexts.get(k));
    }

    public static void waitForStart(@Nonnull String k, @CheckForNull Run b) throws IOException, InterruptedException {
        synchronized (started) {
            while (!started.contains(k)) {
                if (b != null && !b.isBuilding()) {
                    throw new AssertionError(JenkinsRule.getLog(b));
                }
                started.wait(1000);
            }
        }
    }

    public static class Execution extends AbstractStepExecutionImpl {

        @Inject(optional=true) private SemaphoreStep step;

        @Override public boolean start() throws Exception {
            String k = step.k();
            boolean sync = true;
            if (returnValues.containsKey(k)) {
                System.err.println("Immediately running " + k);
                getContext().onSuccess(returnValues.get(k));
            } else if (errors.containsKey(k)) {
                System.err.println("Immediately failing " + k);
                getContext().onFailure(errors.get(k));
            } else {
                System.err.println("Blocking " + k);
                contexts.put(k, Jenkins.XSTREAM.toXML(getContext()));
                sync = false;
            }
            synchronized (started) {
                started.add(k);
                started.notifyAll();
            }
            return sync;
        }

        @Override public void stop(Throwable cause) {
            contexts.remove(step.k());
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
