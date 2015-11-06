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
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Launcher;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Action;
import hudson.model.BuildableItem;
import hudson.model.Cause;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Executor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceList;
import hudson.model.Run;
import hudson.model.RunMap;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.queue.SubTask;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.search.SearchIndexBuilder;
import hudson.security.ACL;
import hudson.slaves.WorkspaceList;
import hudson.tasks.LogRotator;
import hudson.triggers.SCMTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.DescribableList;
import hudson.widgets.HistoryWidget;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import jenkins.model.BuildDiscarder;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.lazy.LazyBuildMixIn;
import jenkins.triggers.SCMTriggerItem;
import jenkins.util.TimeDuration;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinitionDescriptor;
import org.jenkinsci.plugins.workflow.job.properties.BuildDiscarderProperty;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class WorkflowJob extends Job<WorkflowJob,WorkflowRun> implements BuildableItem, LazyBuildMixIn.LazyLoadingJob<WorkflowJob,WorkflowRun>, ParameterizedJobMixIn.ParameterizedJob, TopLevelItem, Queue.FlyweightTask, SCMTriggerItem {

    private FlowDefinition definition;
    private DescribableList<Trigger<?>,TriggerDescriptor> triggers = new DescribableList<Trigger<?>,TriggerDescriptor>(this);
    private volatile Integer quietPeriod;
    @SuppressWarnings("deprecation")
    private hudson.model.BuildAuthorizationToken authToken;
    private transient LazyBuildMixIn<WorkflowJob,WorkflowRun> buildMixIn;
    /** defaults to true */
    private @CheckForNull Boolean concurrentBuild;

    public WorkflowJob(ItemGroup parent, String name) {
        super(parent, name);
        buildMixIn = createBuildMixIn();
    }

    @Override public void onCreatedFromScratch() {
        super.onCreatedFromScratch();
        buildMixIn.onCreatedFromScratch();
    }

    @Override public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        if (buildMixIn == null) {
            buildMixIn = createBuildMixIn();
        }
        buildMixIn.onLoad(parent, name);
        if (triggers == null) {
            triggers = new DescribableList<Trigger<?>,TriggerDescriptor>(this);
        } else {
            triggers.setOwner(this);
        }
        for (Trigger t : triggers) {
            t.start(this, Items.currentlyUpdatingByXml());
        }
    }

    private LazyBuildMixIn<WorkflowJob,WorkflowRun> createBuildMixIn() {
        return new LazyBuildMixIn<WorkflowJob,WorkflowRun>() {
            @Override protected WorkflowJob asJob() {
                return WorkflowJob.this;
            }
            @Override protected Class<WorkflowRun> getBuildClass() {
                return WorkflowRun.class;
            }
        };
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

    @Override protected RunMap<WorkflowRun> _getRuns() {
        return buildMixIn._getRuns();
    }

    @Override public LazyBuildMixIn<WorkflowJob,WorkflowRun> getLazyBuildMixIn() {
        return buildMixIn;
    }

    @Override protected void removeRun(WorkflowRun run) {
        buildMixIn.removeRun(run);
    }

    @Override @Deprecated public WorkflowRun getBuild(String id) {
        return buildMixIn.getBuild(id);
    }

    @Override public WorkflowRun getBuildByNumber(int n) {
        return buildMixIn.getBuildByNumber(n);
    }

    @Override public WorkflowRun getFirstBuild() {
        return buildMixIn.getFirstBuild();
    }

    @Override public WorkflowRun getLastBuild() {
        return buildMixIn.getLastBuild();
    }

    @Override public WorkflowRun getNearestBuild(int n) {
        return buildMixIn.getNearestBuild(n);
    }

    @Override public WorkflowRun getNearestOldBuild(int n) {
        return buildMixIn.getNearestOldBuild(n);
    }

    @Override protected HistoryWidget createHistoryWidget() {
        return buildMixIn.createHistoryWidget();
    }

    @Override public Queue.Executable createExecutable() throws IOException {
        return buildMixIn.newBuild();
    }

    @Deprecated
    @Override public boolean scheduleBuild() {
        return createParameterizedJobMixIn().scheduleBuild();
    }

    @Override public boolean scheduleBuild(Cause c) {
        return createParameterizedJobMixIn().scheduleBuild(c);
    }

    @Deprecated
    @Override public boolean scheduleBuild(int quietPeriod) {
        return createParameterizedJobMixIn().scheduleBuild(quietPeriod);
    }

    @Override public boolean scheduleBuild(int quietPeriod, Cause c) {
        return createParameterizedJobMixIn().scheduleBuild(quietPeriod, c);
    }

    @Override public @CheckForNull QueueTaskFuture<WorkflowRun> scheduleBuild2(int quietPeriod, Action... actions) {
        return createParameterizedJobMixIn().scheduleBuild2(quietPeriod, actions);
    }

    public void doBuild(StaplerRequest req, StaplerResponse rsp, @QueryParameter TimeDuration delay) throws IOException, ServletException {
        createParameterizedJobMixIn().doBuild(req, rsp, delay);
    }

    public void doBuildWithParameters(StaplerRequest req, StaplerResponse rsp, @QueryParameter TimeDuration delay) throws IOException, ServletException {
        createParameterizedJobMixIn().doBuildWithParameters(req, rsp, delay);
    }

    @RequirePOST public void doCancelQueue(StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        createParameterizedJobMixIn().doCancelQueue(req, rsp);
    }

    @Override protected SearchIndexBuilder makeSearchIndex() {
        return createParameterizedJobMixIn().extendSearchIndex(super.makeSearchIndex());
    }

    public boolean isParameterized() {
        return createParameterizedJobMixIn().isParameterized();
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

    @Override public String getBuildNowText() {
        return createParameterizedJobMixIn().getBuildNowText();
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
    // TODO use BlockedBecauseOfBuildInProgress in 1.624 (and remove Messages.properties in resources)
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
                eta = Messages.BlockedBecauseOfBuildInProgress_ETA(e.getEstimatedRemainingTime());
            }
            int lbn = build.getNumber();
            return Messages.BlockedBecauseOfBuildInProgress_shortDescription(lbn, eta);
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

    @Override public ACL getACL() {
        ACL acl = super.getACL();
        for (JobProperty<?> property : properties) {
            if (property instanceof WorkflowJobProperty) {
                acl = ((WorkflowJobProperty) property).decorateACL(acl);
            }
        }
        return acl;
    }

    @Override public void checkAbortPermission() {
        checkPermission(CANCEL);
    }

    @Override public boolean hasAbortPermission() {
        return hasPermission(CANCEL);
    }

    @Override public Collection<? extends SubTask> getSubTasks() {
        // TODO mostly copied from AbstractProject, except SubTaskContributor is not available:
        List<SubTask> subTasks = new ArrayList<SubTask>();
        subTasks.add(this);
        for (JobProperty<? super WorkflowJob> p : properties) {
            subTasks.addAll(p.getSubTasks());
        }
        return subTasks;
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

    @Override public Queue.Task getOwnerTask() {
        return this;
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
        for (WorkflowRun.SCMCheckout co : b.checkouts(null)) {
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
        for (WorkflowRun.SCMCheckout co : b.checkouts(listener)) {
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

    /** Actually it does, but we want to suppress this section of {@code Job/configure.jelly} in favor of {@link BuildDiscarderProperty}. */
    @Override public boolean supportsLogRotator() {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override public LogRotator getLogRotator() {
        BuildDiscarder buildDiscarder = getBuildDiscarder();
        return buildDiscarder instanceof LogRotator ? (LogRotator) buildDiscarder : null;
    }

    @Override public void setBuildDiscarder(BuildDiscarder bd) throws IOException {
        removeProperty(BuildDiscarderProperty.class);
        if (bd != null) {
            addProperty(new BuildDiscarderProperty(bd));
        }
    }

    @Override public BuildDiscarder getBuildDiscarder() {
        BuildDiscarderProperty prop = getProperty(BuildDiscarderProperty.class);
        return prop != null ? prop.getStrategy() : /* settings compatibility */ super.getBuildDiscarder();
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

        /** TODO JENKINS-20020 can delete this in case {@code f:dropdownDescriptorSelector} defaults to applying {@code h.filterDescriptors} */
        @Restricted(DoNotUse.class) // Jelly
        public Collection<FlowDefinitionDescriptor> getDefinitionDescriptors(WorkflowJob context) {
            return DescriptorVisibilityFilter.apply(context, ExtensionList.lookup(FlowDefinitionDescriptor.class));
        }

    }

}
