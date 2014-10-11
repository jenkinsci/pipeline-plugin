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

package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A step that runs a {@link SimpleBuildStep} as defined in Jenkins core.
 */
public final class CoreStep extends Step {

    public final SimpleBuildStep delegate;

    @DataBoundConstructor public CoreStep(SimpleBuildStep delegate) {
        this.delegate = delegate;
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution(delegate, context);
    }

    private static final class Execution extends AbstractSynchronousStepExecution<Void> {

        private transient final SimpleBuildStep delegate;

        Execution(SimpleBuildStep delegate, StepContext context) {
            super(context);
            this.delegate = delegate;
        }

        @Override protected Void run() throws Exception {
            delegate.perform(getContext().get(Run.class), getContext().get(FilePath.class), getContext().get(Launcher.class), getContext().get(TaskListener.class));
            return null;
        }

    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public Set<Class<?>> getRequiredContext() {
            Set<Class<?>> r = new HashSet<Class<?>>();
            r.add(Run.class);
            r.add(FilePath.class);
            r.add(Launcher.class);
            r.add(TaskListener.class);
            return r;
        }

        @Override public String getFunctionName() {
            return "step";
        }

        @Override public Step newInstance(Map<String,Object> arguments) throws Exception {
            String className = (String) arguments.get("$class");
            Class<? extends SimpleBuildStep> c = Jenkins.getInstance().getPluginManager().uberClassLoader.loadClass(className).asSubclass(SimpleBuildStep.class);
            SimpleBuildStep delegate = AbstractStepDescriptorImpl.instantiate(c, arguments);
            return new CoreStep(delegate);
        }

        @Override public Map<String,Object> defineArguments(Step _step) throws UnsupportedOperationException {
            CoreStep step = (CoreStep) _step;
            Map<String,Object> r = AbstractStepDescriptorImpl.uninstantiate(step.delegate);
            r.put("$class", step.delegate.getClass().getName());
            return r;
        }

        @Override public String getDisplayName() {
            return "General Build Step";
        }

        public Collection<? extends Descriptor<?>> getApplicableDescriptors() {
            // Jenkins.instance.getDescriptorList(SimpleBuildStep) is empty, presumably because that itself is not a Describable.
            List<Descriptor<?>> r = new ArrayList<Descriptor<?>>();
            populate(r, Builder.class);
            populate(r, Publisher.class);
            return r;
        }
        private <T extends Describable<T>,D extends Descriptor<T>> void populate(List<Descriptor<?>> r, Class<T> c) {
            for (Descriptor<?> d : Jenkins.getInstance().getDescriptorList(c)) {
                if (SimpleBuildStep.class.isAssignableFrom(d.clazz)) {
                    r.add(d);
                }
            }
        }

    }

}
