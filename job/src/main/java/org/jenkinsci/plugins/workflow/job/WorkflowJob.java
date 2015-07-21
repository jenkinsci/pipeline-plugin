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

package org.jenkinsci.plugins.workflow.job;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Action;
import hudson.model.BuildableItem;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.ResourceList;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.queue.CauseOfBlockage;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.slaves.WorkspaceList;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.triggers.SCMTrigger;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.DescribableList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.lazy.LazyBuildMixIn;
import jenkins.triggers.SCMTriggerItem;
import net.sf.json.JSONObject;

import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class WorkflowJob extends AbstractProject<WorkflowJob,WorkflowRun> implements BuildableItem, LazyBuildMixIn.LazyLoadingJob<WorkflowJob,WorkflowRun>, ParameterizedJobMixIn.ParameterizedJob, TopLevelItem, Queue.FlyweightTask, SCMTriggerItem {

    private FlowDefinition definition;
    private DescribableList<Trigger<?>,TriggerDescriptor> triggers = new DescribableList<Trigger<?>,TriggerDescriptor>(this);
    private volatile Integer quietPeriod;
    @SuppressWarnings("deprecation")
    private hudson.model.BuildAuthorizationToken authToken;
    /** defaults to true */
    private @CheckForNull Boolean concurrentBuild;

    public WorkflowJob(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override public void onCreatedFromScratch() {
        super.onCreatedFromScratch();
    }

    @Override public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        if (triggers == null) {
            triggers = new DescribableList<Trigger<?>,TriggerDescriptor>(this);
        } else {
            triggers.setOwner(this);
        }
        for (Trigger t : triggers) {
            t.start(this, Items.currentlyUpdatingByXml());
        }
    }

    private ParameterizedJobMixIn<WorkflowJob,WorkflowRun> createParameterizedJobMixIn() {
        return new ParameterizedJobMixIn<WorkflowJob,WorkflowRun>() {
            @Override protected WorkflowJob asJob() {
                return WorkflowJob.this;
            }
        };
    }

    public FlowDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(FlowDefinition definition) {
        this.definition = definition;
    }

    @SuppressWarnings("deprecation")
    @Override protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        super.submit(req, rsp);
        JSONObject json = req.getSubmittedForm();
        definition = req.bindJSON(FlowDefinition.class, json.getJSONObject("definition"));
        authToken = hudson.model.BuildAuthorizationToken.create(req);
        for (Trigger t : triggers) {
            t.stop();
        }
        if (req.getParameter("hasCustomQuietPeriod") != null) {
            quietPeriod = Integer.parseInt(req.getParameter("quiet_period"));
        } else {
            quietPeriod = null;
        }
        triggers.rebuild(req, json, Trigger.for_(this));
        for (Trigger t : triggers) {
            t.start(this, true);
        }
        concurrentBuild = json.optBoolean("concurrentBuild") ? null : false;
    }
    
    @Override public boolean isBuildable() {
        return true; // why not?
    }

    @Override public @CheckForNull QueueTaskFuture<WorkflowRun> scheduleBuild2(int quietPeriod, Action... actions) {
        return createParameterizedJobMixIn().scheduleBuild2(quietPeriod, actions);
    }

    @SuppressWarnings("deprecation")
    @Override public hudson.model.BuildAuthorizationToken getAuthToken() {
        return authToken;
    }

    @Override public int getQuietPeriod() {
        return quietPeriod != null ? quietPeriod : Jenkins.getActiveInstance().getQuietPeriod();
    }

    @Restricted(DoNotUse.class) // for config-quietPeriod.jelly
    public boolean getHasCustomQuietPeriod() {
        return quietPeriod!=null;
    }

    public void setQuietPeriod(Integer seconds) throws IOException {
        this.quietPeriod = seconds;
        save();
    }

    @Override public boolean isBuildBlocked() {
        return getCauseOfBlockage() != null;
    }

    @Deprecated
    @Override public String getWhyBlocked() {
        CauseOfBlockage c = getCauseOfBlockage();
        return c != null ? c.getShortDescription() : null;
    }

    @Override public CauseOfBlockage getCauseOfBlockage() {
        if (isLogUpdated() && !isConcurrentBuild()) {
            return new BecauseOfBuildInProgress(getLastBuild());
        }
        return null;
    }
    // TODO delete when https://github.com/jenkinsci/jenkins/pull/1747 is available
    public static class BecauseOfBuildInProgress extends CauseOfBlockage {
        private final Run<?,?> build;

        public BecauseOfBuildInProgress(Run<?, ?> build) {
            this.build = build;
        }

        @Override
        public String getShortDescription() {
            Executor e = build.getExecutor();
            String eta = "";
            if (e != null) {
                eta = hudson.model.Messages.AbstractProject_ETA(e.getEstimatedRemainingTime());
            }
            int lbn = build.getNumber();
            return hudson.model.Messages.AbstractProject_BuildInProgress(lbn, eta);
        }
    }

    @Exported
    @Override public boolean isConcurrentBuild() {
        return !Boolean.FALSE.equals(concurrentBuild);
    }
    
    public void setConcurrentBuild(boolean b) throws IOException {
        concurrentBuild = b ? null : false;
        save();
    }

    @Override public void checkAbortPermission() {
        checkPermission(CANCEL);
    }

    @Override public boolean hasAbortPermission() {
        return hasPermission(CANCEL);
    }

    @Override public Authentication getDefaultAuthentication() {
        return ACL.SYSTEM;
    }

    @Override public Authentication getDefaultAuthentication(Queue.Item item) {
        return getDefaultAuthentication();
    }

    @Override public Label getAssignedLabel() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            return null;
        }
        return j.getSelfLabel();
    }

    @Override public Node getLastBuiltOn() {
        return Jenkins.getInstance();
    }

    @Override public Object getSameNodeConstraint() {
        return this;
    }

    @Override public ResourceList getResourceList() {
        return ResourceList.EMPTY;
    }

    @Override public String getPronoun() {
        return AlternativeUiTextProvider.get(PRONOUN, this, "Workflow");
    }

    @Override public TopLevelItemDescriptor getDescriptor() {
        return (DescriptorImpl) Jenkins.getActiveInstance().getDescriptorOrDie(WorkflowJob.class);
    }

    @Override public Map<TriggerDescriptor, Trigger<?>> getTriggers() {
        return triggers.toMap();
    }

    public void addTrigger(Trigger trigger) {
        Trigger old = triggers.get(trigger.getDescriptor());
        if (old != null) {
            old.stop();
            triggers.remove(old);
        }
        triggers.add(trigger);
        trigger.start(this, true);
    }

    @SuppressWarnings("deprecation")
    @Override public List<Action> getActions() {
        List<Action> actions = new ArrayList<Action>(super.getActions());
        for (Trigger<?> trigger : triggers) {
            actions.addAll(trigger.getProjectActions());
        }
        return actions;
    }

    @Override public Item asItem() {
        return this;
    }

    @Override public SCMTrigger getSCMTrigger() {
        return triggers.get(SCMTrigger.class);
    }

    @Override public Collection<? extends SCM> getSCMs() {
        WorkflowRun b = getLastCompletedBuild();
        if (b == null) {
            return Collections.emptySet();
        }
        List<SCM> scms = new LinkedList<SCM>();
        for (WorkflowRun.SCMCheckout co : b.checkouts) {
            scms.add(co.scm);
        }
        return scms;
    }

    public @CheckForNull SCM getTypicalSCM() {
        SCM typical = null;
        for (SCM scm : getSCMs()) {
            if (typical == null) {
                typical = scm;
            } else if (typical.getDescriptor() != scm.getDescriptor()) {
                return null;
            }
        }
        return typical;
    }

    @Override public PollingResult poll(TaskListener listener) {
        // TODO call SCMPollListener
        WorkflowRun b = getLastCompletedBuild();
        if (b == null) {
            listener.getLogger().println("no previous build to compare to");
            return PollingResult.NO_CHANGES;
        }
        for (WorkflowRun.SCMCheckout co : b.checkouts) {
            if (!co.scm.supportsPolling()) {
                listener.getLogger().println("polling not supported from " + co.workspace + " on " + co.node);
            }
            if (co.pollingBaseline == null) {
                listener.getLogger().println("no polling from " + co.workspace + " on " + co.node);
                continue;
            }
            try {
                FilePath workspace;
                Launcher launcher;
                WorkspaceList.Lease lease;
                if (co.scm.requiresWorkspaceForPolling()) {
                    Jenkins j = Jenkins.getInstance();
                    if (j == null) {
                        listener.error("Jenkins is shutting down");
                        continue;
                    }
                    Computer c = j.getComputer(co.node);
                    if (c == null) {
                        listener.error("no such computer " + co.node);
                        continue;
                    }
                    workspace = new FilePath(c.getChannel(), co.workspace);
                    launcher = workspace.createLauncher(listener).decorateByEnv(getEnvironment(c.getNode(), listener));
                    lease = c.getWorkspaceList().acquire(workspace, !isConcurrentBuild());
                } else {
                    workspace = null;
                    launcher = null;
                    lease = null;
                }
                PollingResult r;
                try {
                    r = co.scm.compareRemoteRevisionWith(this, launcher, workspace, listener, co.pollingBaseline);
                } finally {
                    if (lease != null) {
                        lease.release();
                    }
                }
                // TODO have to also record r.remote so we do not repoll; better to keep a Map<String,SCMRevisionState> pollingBaseline as a field here, initialized from SCMCheckout.pollingBaseline
                if (r.hasChanges()) {
                    return r;
                }
            } catch (AbortException x) {
                listener.error("polling failed in " + co.workspace + " on " + co.node + ": " + x.getMessage());
            } catch (Exception x) {
                x.printStackTrace(listener.error("polling failed in " + co.workspace + " on " + co.node));
            }
        }
        return PollingResult.NO_CHANGES;
    }

    @Override protected void performDelete() throws IOException, InterruptedException {
        super.performDelete();
        // TODO call SCM.processWorkspaceBeforeDeletion
    }

    @Initializer(before=InitMilestone.EXTENSIONS_AUGMENTED)
    public static void alias() {
        Items.XSTREAM2.alias("flow-definition", WorkflowJob.class);
        WorkflowRun.alias();
    }

    @Extension(ordinal=1) public static final class DescriptorImpl extends TopLevelItemDescriptor {

        @Override public String getDisplayName() {
            return "Workflow";
        }

        @Override public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new WorkflowJob(parent, name);
        }

    }

	@Override
	public DescribableList<Publisher, Descriptor<Publisher>> getPublishersList() {
		return null;
	}

	@Override
	protected Class<WorkflowRun> getBuildClass() {
		return WorkflowRun.class;
	}

	@Override
	public boolean isFingerprintConfigured() {
		return false;
	}

}
