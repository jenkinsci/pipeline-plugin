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

package org.jenkinsci.plugins.workflow.support.steps.build;

import hudson.AbortException;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.Run;
import hudson.security.ACL;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.support.actions.EnvironmentAction;

/**
 * Allows {@link Whitelisted} access to selected attributes of a {@link Run} without requiring Jenkins API imports.
 */
public final class RunWrapper implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String externalizableId;
    private final boolean currentBuild;

    public RunWrapper(Run<?,?> build, boolean currentBuild) {
        this.externalizableId = build.getExternalizableId();
        this.currentBuild = currentBuild;
    }

    /**
     * Raw access to the internal build object.
     * Intentionally not {@link Whitelisted}.
     * The result is also not cached, since we want to stop allowing access to a build after it has been deleted.
     */
    public @CheckForNull Run<?,?> getRawBuild() {
        return Run.fromExternalizableId(externalizableId);
    }

    private @Nonnull Run<?,?> build() throws AbortException {
        Run<?,?> r = getRawBuild();
        if (r == null) {
            throw new AbortException("No build record " + externalizableId + " could be located.");
        }
        return r;
    }

    @Whitelisted
    public void setResult(String result) throws AbortException {
        if (!currentBuild) {
            throw new SecurityException("can only set the result property on the current build");
        }
        build().setResult(Result.fromString(result));
    }

    @Whitelisted
    public void setDescription(String d) throws IOException {
        if (!currentBuild) {
            throw new SecurityException("can only set the description property on the current build");
        }
        // Even if the build is carrying a specific authentication, we want it to be allowed to update itself:
        SecurityContext orig = ACL.impersonate(ACL.SYSTEM);
        try {
            build().setDescription(d);
        } finally {
            SecurityContextHolder.setContext(orig);
        }
    }

    @Whitelisted
    public void setDisplayName(String n) throws IOException {
        if (!currentBuild) {
            throw new SecurityException("can only set the displayName property on the current build");
        }
        SecurityContext orig = ACL.impersonate(ACL.SYSTEM);
        try {
            build().setDisplayName(n);
        } finally {
            SecurityContextHolder.setContext(orig);
        }
    }

    @Whitelisted
    public int getNumber() throws AbortException {
        return build().getNumber();
    }

    @Whitelisted
    public @CheckForNull String getResult() throws AbortException {
        Result result = build().getResult();
        return result != null ? result.toString() : null;
    }

    @Whitelisted
    public long getTimeInMillis() throws AbortException {
        return build().getTimeInMillis();
    }

    @Whitelisted
    public long getStartTimeInMillis() throws AbortException {
        return build().getStartTimeInMillis();
    }

    @Whitelisted
    public String getDescription() throws AbortException {
        return build().getDescription();
    }

    @Whitelisted
    public String getDisplayName() throws AbortException {
        return build().getDisplayName();
    }

    @Whitelisted
    public @CheckForNull RunWrapper getPreviousBuild() throws AbortException {
        Run<?,?> previousBuild = build().getPreviousBuild();
        return previousBuild != null ? new RunWrapper(previousBuild, false) : null;
    }

    @Whitelisted
    public @CheckForNull RunWrapper getNextBuild() throws AbortException {
        Run<?,?> nextBuild = build().getNextBuild();
        return nextBuild != null ? new RunWrapper(nextBuild, false) : null;
    }

    @Whitelisted
    public String getId() throws AbortException {
        return build().getId();
    }

    @Whitelisted
    public @Nonnull Map<String,String> getBuildVariables() throws AbortException {
        Run<?,?> build = build();
        if (build instanceof AbstractBuild) {
            return Collections.unmodifiableMap(((AbstractBuild<?,?>) build).getBuildVariables());
        } else {
            EnvironmentAction.IncludingOverrides env = build.getAction(EnvironmentAction.IncludingOverrides.class);
            if (env != null) { // downstream is also WorkflowRun
                return env.getOverriddenEnvironment();
            } else { // who knows
                return Collections.emptyMap();
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Whitelisted
    public @Nonnull String getAbsoluteUrl() throws AbortException {
        return build().getAbsoluteUrl();
    }

}
