/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.multibranch;

import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.model.Queue;
import hudson.scm.SCM;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * Adds an {@code scm} global variable to the script.
 * This makes it possible to run {@code checkout scm} to get your project sources in the right branch.
 * <p>Note that for this to be legal, the {@link SCM} implementation must implement {@link Serializable}.
 * Normally that is no real burden, since it is saved by XStream into a project configuration anyway.
 */
@Extension public class SCMBinder extends GroovyShellDecorator {

    @Override public void configureShell(CpsFlowExecution context, GroovyShell shell) {
        try {
            Queue.Executable exec = context.getOwner().getExecutable();
            if (exec instanceof WorkflowRun) {
                BranchJobProperty property = ((WorkflowRun) exec).getParent().getProperty(BranchJobProperty.class);
                if (property != null) {
                    shell.setVariable("scm", property.getBranch().getScm());
                }
            }
        } catch (IOException x) {
            Logger.getLogger(SCMBinder.class.getName()).log(Level.WARNING, null, x);
        }
    }

}
