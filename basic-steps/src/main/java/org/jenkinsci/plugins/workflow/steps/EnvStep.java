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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class EnvStep extends AbstractStepImpl {

    /**
     * Environment variable overrides.
     * The format is {@code VAR=val}.
     */
    private final List<String> overrides;

    @DataBoundConstructor public EnvStep(List<String> overrides) {
        for (String pair : overrides) {
            if (pair.indexOf('=') == -1) {
                throw new IllegalArgumentException(pair);
            }
        }
        this.overrides = new ArrayList<String>(overrides);
    }
    
    public List<String> getOverrides() {
        return overrides;
    }
    
    public static class Execution extends AbstractStepExecutionImpl {

        private static final long serialVersionUID = 1;
        
        @Inject(optional=true) private transient EnvStep step;
        
        @Override public boolean start() throws Exception {
            getContext().newBodyInvoker().
                withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new ExpanderImpl(step.overrides))).
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
        private ExpanderImpl(List<String> overrides) {
            this.overrides = new HashMap<String,String>();
            for (String pair : overrides) {
                int split = pair.indexOf('=');
                assert split != -1;
                this.overrides.put(pair.substring(0, split), pair.substring(split + 1));
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

        // TODO JENKINS-27901: need a standard control for this
        @Override public Step newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            String overridesS = formData.getString("overrides");
            List<String> overrides = new ArrayList<String>();
            for (String line : overridesS.split("\r?\n")) {
                line = line.trim();
                if (!line.isEmpty()) {
                    overrides.add(line);
                }
            }
            return new EnvStep(overrides);
        }

    }

}
