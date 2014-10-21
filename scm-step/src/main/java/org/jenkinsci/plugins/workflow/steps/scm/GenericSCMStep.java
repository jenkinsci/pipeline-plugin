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

package org.jenkinsci.plugins.workflow.steps.scm;

import hudson.Extension;
import hudson.model.Job;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.structs.DescribableHelper;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Runs a user-selected {@link SCM}.
 */
public final class GenericSCMStep extends SCMStep {

    private final SCM scm;

    @DataBoundConstructor public GenericSCMStep(SCM scm) {
        this.scm = scm;
    }

    public SCM getSCM() {
        return scm;
    }

    @Override protected SCM createSCM() {
        return scm;
    }

    @Extension public static final class DescriptorImpl extends SCMStepDescriptor {

        @Override public String getFunctionName() {
            return "scm";
        }

        @Override public Step newInstance(Map<String,Object> arguments) throws Exception {
            String className = (String) arguments.get("$class");
            Class<? extends SCM> c = Jenkins.getInstance().getPluginManager().uberClassLoader.loadClass(className).asSubclass(SCM.class);
            SCM scm = DescribableHelper.instantiate(c, arguments);
            GenericSCMStep step = new GenericSCMStep(scm);
            Boolean poll = (Boolean) arguments.get("poll");
            if (poll != null) {
                step.setPoll(poll);
            }
            Boolean changelog = (Boolean) arguments.get("changelog");
            if (changelog != null) {
                step.setChangelog(changelog);
            }
            return step;
        }

        @Override public Map<String,Object> defineArguments(Step _step) throws UnsupportedOperationException {
            GenericSCMStep step = (GenericSCMStep) _step;
            Map<String,Object> r = DescribableHelper.uninstantiate(step.scm);
            r.put("$class", step.scm.getClass().getName());
            r.put("poll", step.isPoll()); // could instead set to false iff Boolean.FALSE.equals(poll), though the super definition using uninstantiate does not grok default values
            r.put("changelog", step.isChangelog());
            return r;
        }

        @Override public String getDisplayName() {
            return "General SCM";
        }

        public Collection<? extends SCMDescriptor<?>> getApplicableDescriptors(@CheckForNull Job<?,?> job) {
            List<SCMDescriptor<?>> r = new ArrayList<SCMDescriptor<?>>();
            for (SCMDescriptor<?> d : SCM.all()) {
                if (job == null || d.isApplicable(job)) {
                    r.add(d);
                }
            }
            return r;
        }

    }

}
