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

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import hudson.Extension;
import hudson.model.TaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Kohsuke Kawaguchi
 */
public class EchoStep extends Step {
    private final String message;

    @DataBoundConstructor
    public EchoStep(String message) {
        this.message = message;
    }

    @Override
    public boolean start(StepContext context) throws Exception {
        context.get(TaskListener.class).getLogger().println(message);
        return true;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "echo";
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) {
            return new EchoStep((String) arguments.get("value"));
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return Collections.<Class<?>>singleton(TaskListener.class);
        }


        @Override
        public String getDisplayName() {
            return "Print Message";
        }
    }
}
