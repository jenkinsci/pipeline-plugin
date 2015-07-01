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

import com.thoughtworks.xstream.converters.Converter;
import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.model.Queue;
import hudson.scm.SCM;
import hudson.util.DescribableList;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.pickles.Pickle;
import org.jenkinsci.plugins.workflow.support.pickles.SingleTypedPickleFactory;
import org.jenkinsci.plugins.workflow.support.pickles.XStreamPickle;

/**
 * Adds an {@code scm} global variable to the script.
 * This makes it possible to run {@code checkout scm} to get your project sources in the right branch.
 */
@Extension public class SCMBinder extends GroovyShellDecorator {

    @Override public void configureShell(CpsFlowExecution context, GroovyShell shell) {
        if (context == null) {
            return;
        }
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

    /**
     * Ensures that {@code scm} is saved in its XML representation.
     * Necessary for {@code GitSCM} which is marked {@link Serializable}
     * yet includes a {@link DescribableList} which relies on a custom {@link Converter}.
     */
    @Extension public static class Pickler extends SingleTypedPickleFactory<SCM> {

        @Override protected Pickle pickle(SCM scm) {
            return new XStreamPickle(scm);
        }

    }

}
