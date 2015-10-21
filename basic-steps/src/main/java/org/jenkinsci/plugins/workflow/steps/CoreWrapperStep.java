/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

import com.google.inject.Inject;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildWrapper;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A step that runs a {@link SimpleBuildWrapper} as defined in Jenkins core.
 */
public class CoreWrapperStep extends AbstractStepImpl {

    private final SimpleBuildWrapper delegate;

    @DataBoundConstructor public CoreWrapperStep(SimpleBuildWrapper delegate) {
        this.delegate = delegate;
    }

    public SimpleBuildWrapper getDelegate() {
        return delegate;
    }

    public static final class Execution extends AbstractStepExecutionImpl {

        private static final long serialVersionUID = 1;

        @Inject(optional=true) private transient CoreWrapperStep step;
        @StepContextParameter private transient Run<?,?> run;
        @StepContextParameter private transient FilePath workspace;
        @StepContextParameter private transient Launcher launcher;
        @StepContextParameter private transient TaskListener listener;
        @StepContextParameter private transient EnvVars env;

        @Override public boolean start() throws Exception {
            SimpleBuildWrapper.Context c = new SimpleBuildWrapper.Context();
            step.delegate.setUp(c, run, workspace, launcher, listener, env);
            BodyInvoker bodyInvoker = getContext().newBodyInvoker();
            Map<String,String> overrides = c.getEnv();
            if (!overrides.isEmpty()) {
                bodyInvoker.withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new ExpanderImpl(overrides)));
            }
            ConsoleLogFilter filter = step.delegate.createLoggerDecorator(run);
            if (filter != null) {
                bodyInvoker.withContext(BodyInvoker.mergeConsoleLogFilters(getContext().get(ConsoleLogFilter.class), filter));
            }
            SimpleBuildWrapper.Disposer disposer = c.getDisposer();
            bodyInvoker.withCallback(disposer != null ? new Callback(disposer) : BodyExecutionCallback.wrap(getContext())).start();
            return false;
        }

        @Override public void stop(Throwable cause) throws Exception {
            // should be no need to do anything special (but verify in JENKINS-26148)
        }

    }

    private static final class ExpanderImpl extends EnvironmentExpander {
        private static final long serialVersionUID = 1;
        private final Map<String,String> overrides;
        ExpanderImpl(Map<String,String> overrides) {
            this.overrides = /* ensure serializability*/ new HashMap<String,String>(overrides);
        }
        @Override public void expand(EnvVars env) throws IOException, InterruptedException {
            env.overrideExpandingAll(overrides);
        }
    }

    private static final class Callback extends BodyExecutionCallback.TailCall {

        private static final long serialVersionUID = 1;

        private final @Nonnull SimpleBuildWrapper.Disposer disposer;

        Callback(@Nonnull SimpleBuildWrapper.Disposer disposer) {
            this.disposer = disposer;
        }

        @Override protected void finished(StepContext context) throws Exception {
            disposer.tearDown(context.get(Run.class), context.get(FilePath.class), context.get(Launcher.class), context.get(TaskListener.class));
        }

    }

    @Extension public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "wrap";
        }

        @Override public String getDisplayName() {
            return "General Build Wrapper";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        // getPropertyType("delegate").getApplicableDescriptors() does not work, because extension lists do not work on subtypes.
        public Collection<BuildWrapperDescriptor> getApplicableDescriptors() {
            Collection<BuildWrapperDescriptor> r = new ArrayList<BuildWrapperDescriptor>();
            for (BuildWrapperDescriptor d : Jenkins.getActiveInstance().getExtensionList(BuildWrapperDescriptor.class)) {
                if (SimpleBuildWrapper.class.isAssignableFrom(d.clazz)) {
                    r.add(d);
                }
            }
            return r;
        }

    }

}
