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

package org.jenkinsci.plugins.workflow.cps;

import hudson.model.Result;
import hudson.model.Run;
import hudson.security.ACL;
import java.io.IOException;
import java.io.Serializable;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.support.pickles.RunPickle;

/**
 * Allows access to some privileged methods of the current build only, as well as some otherwise whitelisted {@link Run} methods.
 * <p>Note that though this is {@link Serializable}, the actual serialized field becomes a {@link RunPickle}.
 * (Alternately could keep a reference to the {@link CpsScript} and call {@link CpsScript#build} on demand.)
 */
final class CurrentBuildWrapper implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Run<?,?> build;

    CurrentBuildWrapper(Run<?,?> build) {
        this.build = build;
    }

    @Whitelisted
    public void setResult(Result r) {
        build.setResult(r);
    }

    @Whitelisted
    public void setDescription(String d) throws IOException {
        // Even if the build is carrying a specific authentication, we want it to be allowed to update itself:
        SecurityContext orig = ACL.impersonate(ACL.SYSTEM);
        try {
            build.setDescription(d);
        } finally {
            SecurityContextHolder.setContext(orig);
        }
    }

    @Whitelisted
    public void setDisplayName(String n) throws IOException {
        SecurityContext orig = ACL.impersonate(ACL.SYSTEM);
        try {
            build.setDisplayName(n);
        } finally {
            SecurityContextHolder.setContext(orig);
        }
    }

    /* TODO extending GroovyObjectSupport (and making CpsWhitelist.permitsMethod whitelist this class)
       would avoid the need to hardcode methods already whitelisted for Run, but this did not work out;
       no easy way to do a whitelist check on delegate methods since GroovyCallSiteSelector is not public:
    private MetaClass metaClass() {
        return InvokerHelper.getMetaClass(build);
    }

    @Override public Object invokeMethod(String name, Object args) {
        return metaClass().invokeMethod(build, name, args);
    }

    @Override public Object getProperty(String property) {
        return metaClass().getProperty(build, property);
    }

    @Override public void setProperty(String property, Object newValue) {
        metaClass().setProperty(build, property, newValue);
    }
    */

    @Whitelisted
    public int getNumber() {
        return build.getNumber();
    }

    @Whitelisted
    public Result getResult() {
        return build.getResult();
    }

    @Whitelisted
    public long getTimeInMillis() {
        return build.getTimeInMillis();
    }

    @Whitelisted
    public long getStartTimeInMillis() {
        return build.getStartTimeInMillis();
    }

    @Whitelisted
    public String getDescription() {
        return build.getDescription();
    }

    @Whitelisted
    public String getDisplayName() {
        return build.getDisplayName();
    }

    @Whitelisted
    public Run<?,?> getPreviousBuild() {
        return build.getPreviousBuild();
    }

    @Whitelisted
    public Run<?,?> getNextBuild() {
        return build.getNextBuild();
    }

    @Whitelisted
    public String getId() {
        return build.getId();
    }

}
