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

package org.jenkinsci.plugins.workflow.steps;

import com.google.common.util.concurrent.FutureCallback;
import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import java.util.Map;
import java.util.Set;

/**
 * @author Kohsuke Kawaguchi
 * @author Jesse Glick
 */
public abstract class StepDescriptor extends Descriptor<Step> {
    /**
     * Returns the context {@link Step} needs to access.
     *
     * This allows the system to statically infer which steps are applicable in which context
     * (say in freestyle or in workflow).
     * @see StepContext#getContext
     */
    public abstract Set<Class<?>> getRequiredContext();

    /**
     * Return a short string that is a valid identifier for programming languages
     * ([A-Za-z_] in the first char and [A-Za-z0-9_]" for all the other chars.
     *
     * Step will be referenced by this name when used in a programming language.
     */
    public abstract String getFunctionName();

    /**
     * Returns true if this step can accept implicit block as the argument.
     *
     * @see StepContext#invokeBodyLater(FutureCallback, Object...)
     */
    public boolean takesImplicitBlockArgument() {
        return false;
    }

    /**
     * Used when a {@link Step} is instantiated programmatically.
     *
     * @param arguments
     *      Named arguments and values, à la Ant task or Maven mojos.
     * @return an instance of {@link #clazz}
     */
    public abstract Step newInstance(Map<String,Object> arguments) throws Exception;

    public static DescriptorExtensionList<Step,StepDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(Step.class);
    }

    public static StepDescriptor getByFunctionName(String functionName) {
        for (StepDescriptor d : all()) {
            if (d.getFunctionName().equals(functionName))
                return d;
        }
        return null;
    }
}
