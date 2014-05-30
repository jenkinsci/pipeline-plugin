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

import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Action;
import hudson.model.BuildableItem;
import hudson.model.Cause;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.PermalinkProjectAction;
import hudson.model.Queue;
import hudson.model.ResourceList;
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
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.lazy.LazyBuildMixIn;
import jenkins.triggers.SCMTriggerItem;
import jenkins.util.TimeDuration;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class WorkflowJob extends Job<WorkflowJob,WorkflowRun> implements BuildableItem, ModelObjectWithChildren, LazyBuildMixIn.LazyLoadingJob<WorkflowJob,WorkflowRun>, ParameterizedJobMixIn.ParameterizedJob, TopLevelItem, Queue.FlyweightTask, SCMTriggerItem {

    private FlowDefinition definition;
    private DescribableList<Trigger<?>,TriggerDescriptor> triggers = new DescribableList<Trigger<?>,TriggerDescriptor>(this);
    @SuppressWarnings("deprecation")
    private hudson.model.BuildAuthorizationToken authToken;
    private transient LazyBuildMixIn<WorkflowJob,WorkflowRun> buildMixIn;

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
        definition = req.bindJSON(FlowDefinition.class, req.getSubmittedForm().getJSONObject("definition"));
        authToken = hudson.model.BuildAuthorizationToken.create(req);
        for (Trigger t : triggers) {
            t.stop();
        }
        triggers.rebuild(req, req.getSubmittedForm(), Trigger.for_(this));
        for (Trigger t : triggers) {
            t.start(this, true);
        }
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

    @Override public boolean scheduleBuild() {
        return createParameterizedJobMixIn().scheduleBuild();
    }

    @Override public boolean scheduleBuild(Cause c) {
        return createParameterizedJobMixIn().scheduleBuild(c);
    }

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
        return Jenkins.getInstance().getQuietPeriod();
    }

    @Override public String getBuildNowText() {
        return createParameterizedJobMixIn().getBuildNowText();
    }

    @Override public boolean isBuildBlocked() {
        return getCauseOfBlockage() != null;
    }

    @Override public String getWhyBlocked() {
        CauseOfBlockage c = getCauseOfBlockage();
        return c != null ? c.getShortDescription() : null;
    }

    @Override public CauseOfBlockage getCauseOfBlockage() {
        // TODO is there any legitimate reason to block a flow? maybe !isConcurrentBuild()?
        return null;
    }

    @Override public boolean isConcurrentBuild() {
        // TODO should this be configurable?
        return true;
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

    @Override public Label getAssignedLabel() {
        return Jenkins.getInstance().getSelfLabel();
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

    @Override public ContextMenu doChildrenContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        // TODO copied from AbstractProject:
        ContextMenu menu = new ContextMenu();
        for (PermalinkProjectAction.Permalink p : getPermalinks()) {
            if (p.resolve(this) != null) {
                menu.add(p.getId(), p.getDisplayName());
            }
        }
        return menu;
    }

    @Override public String getPronoun() {
        return AlternativeUiTextProvider.get(PRONOUN, this, "Workflow");
    }

    @Override public TopLevelItemDescriptor getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(WorkflowJob.class);
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
        return Collections.emptySet(); // TODO
    }

    @Initializer(before=InitMilestone.EXTENSIONS_AUGMENTED)
    public static void alias() {
        Items.XSTREAM2.alias("flow-definition", WorkflowJob.class);
        WorkflowRun.alias();
    }

    @Override public PollingResult poll(TaskListener listener) {
        return PollingResult.NO_CHANGES; // TODO
    }

    @Extension public static final class DescriptorImpl extends TopLevelItemDescriptor {

        @Override public String getDisplayName() {
            return "Workflow";
        }

        @Override public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new WorkflowJob(parent, name);
        }

    }

}
