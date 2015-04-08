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
import hudson.model.Item;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import static org.junit.Assert.*;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Utility to let you verify how {@link Step}s are configured in the UI.
 */
public final class StepConfigTester {

    private final JenkinsRule rule;

    public StepConfigTester(JenkinsRule rule) {
        this.rule = rule;
    }

    /**
     * Akin to {@link JenkinsRule#configRoundtrip(Builder)}.
     * @param <S> step type
     * @param before the incoming step
     * @return the result after displaying in a form and resaving
     */
    @SuppressWarnings("unchecked")
    public <S extends Step> S configRoundTrip(S before) throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(new StepBuilder(before));
        // workaround for eclipse compiler Ambiguous method call
        p = (FreeStyleProject) rule.configRoundtrip((Item)p);
        StepBuilder b = p.getBuildersList().get(StepBuilder.class);
        assertNotNull(b);
        Step after = b.s;
        assertNotNull(after);
        assertEquals(before.getClass(), after.getClass());
        return (S) after;
    }

    @Restricted(NoExternalUse.class)
    public static final class StepBuilder extends Builder {
        public final Step s;
        @DataBoundConstructor public StepBuilder(Step s) {
            this.s = s;
        }
        @Extension public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
            @SuppressWarnings("rawtypes")
            @Override public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }
            @Override public String getDisplayName() {
                return "Test step builder";
            }
            @Override public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
                // TODO f:dropdownDescriptorSelector does not seem to work sensibly: the super method uses RequestImpl.bindJSON and ignores any StepDescriptor.newInstance override.
                // Cf. Snippetizer.doGenerateSnippet, which also seems to lack a standard way of parsing part of a form using databinding.
                JSONObject s = formData.getJSONObject("s");
                Jenkins j = Jenkins.getActiveInstance();
                Class<?> c;
                try {
                    c = j.getPluginManager().uberClassLoader.loadClass(s.getString("stapler-class"));
                } catch (ClassNotFoundException x) {
                    throw new FormException(x, "s");
                }
                Descriptor<?> descriptor = j.getDescriptor(c.asSubclass(Step.class));
                return new StepBuilder((Step) descriptor.newInstance(req, s));
            }
        }
    }

}
