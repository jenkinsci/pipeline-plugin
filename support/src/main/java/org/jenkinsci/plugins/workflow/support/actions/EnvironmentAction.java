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

package org.jenkinsci.plugins.workflow.support.actions;

import hudson.EnvVars;
import hudson.model.Action;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Map;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.support.DefaultStepContext;

/**
 * A {@linkplain Run#addAction run action} which reports environment variables.
 * If present, will be used from {@link DefaultStepContext#get} on {@link EnvVars}
 * after amendment by {@link EnvironmentExpander#getEffectiveEnvironment}.
 */
public interface EnvironmentAction extends Action {

    /**
     * Gets the complete global environment for a build, including both {@link Run#getEnvironment(TaskListener)} and any {@link IncludingOverrides#getOverriddenEnvironment}.
     */
    EnvVars getEnvironment() throws IOException, InterruptedException;

    /**
     * Optional extension interface that allows the overrides to be distinguished.
     */
    interface IncludingOverrides extends EnvironmentAction {

        /**
         * Gets any environment variables set during this build that were not originally present.
         */
        Map<String,String> getOverriddenEnvironment();

    }

}
