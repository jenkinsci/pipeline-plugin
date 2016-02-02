/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.cps.rerun;

import com.google.common.collect.ImmutableList;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.queue.QueueTaskFuture;
import hudson.security.Permission;
import hudson.security.PermissionScope;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.TransientActionFactory;
import jenkins.scm.api.SCMRevisionAction;
import org.acegisecurity.AccessDeniedException;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Attached to a {@link WorkflowRun} when it could be rerun.
 */
@SuppressWarnings("rawtypes") // on Run
public class RerunAction implements Action {

    private final Run run;

    private RerunAction(Run run) {
        this.run = run;
    }

    @Override public String getDisplayName() {
        return "Rerun with Edits";
    }

    @Override public String getIconFileName() {
        return isEnabled() ? "clock.png" : null;
    }

    @Override public String getUrlName() {
        return isEnabled() ? "rerun" : null;
    }

    private @CheckForNull CpsFlowExecution getExecution() {
        FlowExecution exec = ((FlowExecutionOwner.Executable) run).asFlowExecutionOwner().getOrNull();
        return exec instanceof CpsFlowExecution ? (CpsFlowExecution) exec : null;
    }

    /* accessible to Jelly */ public boolean isEnabled() {
        if (!run.hasPermission(RERUN)) {
            return false;
        }
        CpsFlowExecution exec = getExecution();
        if (exec == null) {
            return false;
        }
        if (exec.isSandbox()) {
            return true;
        } else {
            // Whole-script approval mode. Can we submit an arbitrary script right here?
            return Jenkins.getActiveInstance().hasPermission(Jenkins.RUN_SCRIPTS);
        }
    }

    /* accessible to Jelly */ public String getOriginalScript() throws Exception {
        return getExecution().getScript();
    }

    /* accessible to Jelly */ public Run getOwner() {
        return run;
    }

    @RequirePOST
    public void doRun(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        if (!isEnabled()) {
            throw new AccessDeniedException("not allowed to rerun"); // AccessDeniedException2 requires us to look up the specific Permission
        }
        run(req.getSubmittedForm().getString("script"));
        rsp.sendRedirect("../.."); // back to WorkflowJob; new build might not start instantly so cannot redirect to it
    }

    private static final Iterable<Class<? extends Action>> COPIED_ACTIONS = ImmutableList.of(
        ParametersAction.class,
        SCMRevisionAction.class
    );

    /** For whitebox testing. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public @CheckForNull QueueTaskFuture/*<Run>*/ run(String script) {
        List<Action> actions = new ArrayList<Action>();
        actions.add(new RerunFlowFactoryAction(script, getExecution().isSandbox()));
        actions.add(new CauseAction(new Cause.UserIdCause(), new RerunCause(run)));
        for (Class<? extends Action> c : COPIED_ACTIONS) {
            actions.addAll(run.getActions(c));
        }
        return new ParameterizedJobMixIn() {
            @Override protected Job asJob() {
                return run.getParent();
            }
        }.scheduleBuild2(0, actions.toArray(new Action[actions.size()]));
    }

    public static final Permission RERUN = new Permission(Run.PERMISSIONS, "Rerun", Messages._Rerun_permission_description(), Item.CONFIGURE, PermissionScope.RUN);

    @Extension public static class Factory extends TransientActionFactory<Run> {

        static {
            RERUN.getEnabled(); // force registration during startup
        }

        @Override public Class<Run> type() {
            return Run.class;
        }

        @Override public Collection<? extends Action> createFor(Run run) {
            return run instanceof FlowExecutionOwner.Executable && run.getParent() instanceof ParameterizedJobMixIn.ParameterizedJob ? Collections.<Action>singleton(new RerunAction(run)) : Collections.<Action>emptySet();
        }

    }

}
