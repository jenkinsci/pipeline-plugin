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

package org.jenkinsci.plugins.workflow.cps;

import groovy.lang.GroovyObjectSupport;
import hudson.EnvVars;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.util.LogTaskListener;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.RunAction2;
import org.jenkinsci.plugins.workflow.support.DefaultStepContext;
import org.jenkinsci.plugins.workflow.support.actions.EnvironmentAction;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class EnvActionImpl extends GroovyObjectSupport implements EnvironmentAction, Serializable, RunAction2 {

    private static final Logger LOGGER = Logger.getLogger(EnvActionImpl.class.getName());
    private static final long serialVersionUID = 1;

    private final Map<String,String> env;
    private transient EnvVars ownerEnvironment; // cache
    private transient Run<?,?> owner;

    EnvActionImpl() {
        this.env = new TreeMap<String,String>();
    }

    @Override public EnvVars getEnvironment() throws IOException, InterruptedException {
        if (ownerEnvironment == null) {
            ownerEnvironment = DefaultStepContext.getEnvironment(owner, new LogTaskListener(LOGGER, Level.INFO));
        }
        EnvVars e = new EnvVars(ownerEnvironment);
        e.putAll(env);
        return e;
    }

    @Exported(name="environment")
    public Map<String,String> getOverriddenEnvironment() {
        return Collections.unmodifiableMap(env);
    }

    @Override public Object getProperty(String propertyName) {
        try {
            String val = getEnvironment().get(propertyName);
            if (val == null) {
                Computer computer = CpsThread.current().getContextVariable(Computer.class);
                if (computer != null) {
                    return computer.getEnvironment().get(propertyName);
                }
            }
            return val;
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, null, x);
            return null;
        }
    }

    @Override public void setProperty(String propertyName, Object newValue) {
        env.put(propertyName, String.valueOf(newValue));
        try {
            owner.save();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    @Override public String getIconFileName() {
        return null;
    }

    @Override public String getDisplayName() {
        return null;
    }

    @Override public String getUrlName() {
        return null;
    }

    @Override public void onAttached(Run<?,?> r) {
        owner = r;
    }

    @Override public void onLoad(Run<?,?> r) {
        owner = r;
    }

}
