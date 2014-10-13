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
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import static org.junit.Assert.*;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

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
        try {
            p.getBuildersList().add(new StepBuilder(before));
            p = rule.configRoundtrip(p);
            StepBuilder b = p.getBuildersList().get(StepBuilder.class);
            assertNotNull(b);
            Step after = b.s;
            assertNotNull(after);
            assertEquals(before.getClass(), after.getClass());
            return (S) after;
        } finally {
            p.delete();
        }
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
        }
    }

}
