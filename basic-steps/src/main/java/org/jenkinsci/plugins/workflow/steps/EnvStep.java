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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.kohsuke.stapler.DataBoundConstructor;

public class EnvStep extends AbstractStepImpl {

    /**
     * Environment variable overrides.
     * The format is <pre>{@code VAR1=val1
     * VAR2=val2}</pre> (pairs separated by newlines).
     * Logically this should be a {@code Map<String,String>} but there is no standard control for that.
     * Ditto for {@code List<String>} or {@code String[]}.
     * Cf. JENKINS-26143 regarding ChoiceParameterDefinition.
     */
    private final String overrides;

    @DataBoundConstructor public EnvStep(String overrides) {
        this.overrides = overrides;
    }
    
    public String getOverrides() {
        return overrides;
    }
    
    public static class Execution extends AbstractStepExecutionImpl {

        private static final long serialVersionUID = 1;
        
        @Inject(optional=true) private transient EnvStep step;
        
        @Override public boolean start() throws Exception {
            getContext().newBodyInvoker().
                withContext(new ExpanderImpl(step.overrides)).
                withCallback(BodyExecutionCallback.wrap(getContext())).
                start();
            return false;
        }
        
        @Override public void stop(Throwable cause) throws Exception {
            // should be no need to do anything special (but verify in JENKINS-26148)
        }
        
    }
    
    private static final class ExpanderImpl extends EnvironmentExpander {
        private static final long serialVersionUID = 1;
        private final Map<String,String> overrides;
        private ExpanderImpl(String overrides) {
            this.overrides = new HashMap<String,String>();
            for (String line : overrides.split("\r?\n")) {
                line = line.trim();
                if (!line.isEmpty()) {
                    int split = line.indexOf('=');
                    if (split == -1) {
                        continue; // ?
                    }
                    this.overrides.put(line.substring(0, split), line.substring(split + 1));
                }
            }
        }
        @Override public void expand(EnvVars env) throws IOException, InterruptedException {
            env.overrideAll(overrides);
        }
    }

    @Extension public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "withEnv";
        }

        @Override public String getDisplayName() {
            return "Set environment variables";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

    }

}
