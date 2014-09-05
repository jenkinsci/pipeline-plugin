package org.jenkinsci.plugins.workflow.support.steps;

import hudson.AbortException;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StageStepExecution extends StepExecution {
    private static final Logger LOGGER = Logger.getLogger(StageStepExecution.class.getName());

    // only used during the start() call, so no need to be persisted
    private transient final StageStep step;

    public StageStepExecution(StageStep step, StepContext context) {
        super(context);
        this.step = step;
    }

    @Override
    public boolean start() throws Exception {
        FlowNode n = getContext().get(FlowNode.class);
        n.addAction(new LabelAction(step.name));
        Run<?,?> r = getContext().get(Run.class);
        enter(r, getContext(), step.name, step.concurrency);
        return false; // execute asynchronously
    }

    @Override
    public void stop() throws Exception {
        // TODO
        throw new UnsupportedOperationException();
    }

    private static XmlFile getConfigFile() {
        return new XmlFile(new File(Jenkins.getInstance().getRootDir(), StageStep.class.getName() + ".xml"));
    }

    // TODO can this be replaced with StepExecutionIterator?
    private static Map<String,Map<String,Stage>> stagesByNameByJob;

    // TODO or delete and make this an instance field in DescriptorImpl
    public static void clear() {
        stagesByNameByJob = null;
    }

    @SuppressWarnings("unchecked")
    private static synchronized void load() {
        if (stagesByNameByJob == null) {
            stagesByNameByJob = new TreeMap<String,Map<String,Stage>>();
            XmlFile configFile = getConfigFile();
            if (configFile.exists()) {
                try {
                    stagesByNameByJob = (Map<String,Map<String,Stage>>) configFile.read();
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, null, x);
                }
            }
            LOGGER.log(Level.FINE, "load: {0}", stagesByNameByJob);
        }
    }

    private static synchronized void save() {
        try {
            getConfigFile().write(stagesByNameByJob);
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
        }
        LOGGER.log(Level.FINE, "save: {0}", stagesByNameByJob);
    }

    private static synchronized void enter(Run<?,?> r, StepContext context, String name, Integer concurrency) {
        LOGGER.log(Level.FINE, "enter {0} {1}", new Object[] {r, name});
        println(context, "Entering stage " + name);
        load();
        Job<?,?> job = r.getParent();
        String jobName = job.getFullName();
        Map<String,Stage> stagesByName = stagesByNameByJob.get(jobName);
        if (stagesByName == null) {
            stagesByName = new TreeMap<String,Stage>();
            stagesByNameByJob.put(jobName, stagesByName);
        }
        Stage stage = stagesByName.get(name);
        if (stage == null) {
            stage = new Stage();
            stagesByName.put(name, stage);
        }
        stage.concurrency = concurrency;
        int build = r.number;
        if (stage.waitingContext != null) {
            // Someone has got to give up.
            if (stage.waitingBuild < build) {
                // Cancel the older one.
                try {
                    cancel(stage.waitingContext, context);
                } catch (Exception x) {
                    LOGGER.log(Level.WARNING, "could not cancel an older flow (perhaps since deleted?)", x);
                }
            } else if (stage.waitingBuild > build) {
                // Cancel this one. And work with the older one below, instead of the one initiating this call.
                try {
                    cancel(context, stage.waitingContext);
                } catch (Exception x) {
                    LOGGER.log(Level.WARNING, "could not cancel the current flow", x);
                }
                build = stage.waitingBuild;
                context = stage.waitingContext;
            } else {
                throw new IllegalStateException("the same flow is trying to reënter the stage " + name); // see 'e' with two dots, that's Jesse Glick for you! - KK
            }
        }
        for (Map.Entry<String,Stage> entry : stagesByName.entrySet()) {
            if (entry.getKey().equals(name)) {
                continue;
            }
            Stage stage2 = entry.getValue();
            // If we were holding another stage in the same job, release it, unlocking its waiter to proceed.
            if (stage2.holding.remove(build)) {
                if (stage2.waitingContext != null) {
                    println(stage2.waitingContext, "Unblocked since " + r.getDisplayName() + " is moving into stage " + name);
                    stage2.waitingContext.onSuccess(null);
                    stage2.waitingBuild = null;
                    stage2.waitingContext = null;
                }
            }
        }
        if (stage.concurrency == null || stage.holding.size() < stage.concurrency) {
            stage.waitingBuild = null;
            stage.waitingContext = null;
            stage.holding.add(build);
            println(context, "Proceeding");
            context.onSuccess(null);
        } else {
            stage.waitingBuild = build;
            stage.waitingContext = context;
            println(context, "Waiting for builds " + stage.holding);
        }
        cleanUp(job, jobName);
        save();
    }

    private static synchronized void exit(Run<?,?> r) {
        load();
        LOGGER.log(Level.FINE, "exit {0}: {1}", new Object[] {r, stagesByNameByJob});
        Job<?,?> job = r.getParent();
        String jobName = job.getFullName();
        Map<String,Stage> stagesByName = stagesByNameByJob.get(jobName);
        if (stagesByName == null) {
            return;
        }
        boolean modified = false;
        for (Stage stage : stagesByName.values()) {
            if (stage.holding.contains(r.number)) {
                stage.holding.remove(r.number); // XSTR-757: do not rely on return value of TreeSet.remove(Object)
                modified = true;
                if (stage.waitingContext != null) {
                    println(stage.waitingContext, "Unblocked since " + r.getDisplayName() + " finished");
                    stage.waitingContext.onSuccess(null);
                    stage.waitingContext = null;
                    stage.waitingBuild = null;
                }
            }
        }
        if (modified) {
            cleanUp(job, jobName);
            save();
        }
    }

    private static void cleanUp(Job<?,?> job, String jobName) {
        Map<String,Stage> stagesByName = stagesByNameByJob.get(jobName);
        assert stagesByName != null;
        Iterator<Entry<String,Stage>> it = stagesByName.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String,Stage> entry = it.next();
            Set<Integer> holding = entry.getValue().holding;
            Iterator<Integer> it2 = holding.iterator();
            while (it2.hasNext()) {
                Integer number = it2.next();
                if (job.getBuildByNumber(number) == null) {
                    // Deleted at some point but did not properly clean up from exit(…).
                    LOGGER.log(Level.WARNING, "Cleaning up apparently deleted {0}#{1}", new Object[] {jobName, number});
                    it2.remove();
                }
            }
            if (holding.isEmpty()) {
                assert entry.getValue().waitingContext == null;
                it.remove();
            }
        }
        if (stagesByName.isEmpty()) {
            stagesByNameByJob.remove(jobName);
        }
    }

    private static void println(StepContext context, String message) {
        try {
            context.get(TaskListener.class).getLogger().println(message);
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, null, x);
        }
    }

    // TODO record the stage it got to and display that
    private static void cancel(StepContext context, StepContext newer) throws IOException, InterruptedException {
        println(context, "Canceled since " + newer.get(Run.class).getDisplayName() + " got here");
        println(newer, "Canceling older " + context.get(Run.class).getDisplayName());
        context.get(Run.class).addAction(new InterruptedBuildAction(Collections.singleton(new CanceledCause(newer.get(Run.class)))));
        /* TODO not yet implemented
        context.get(FlowExecution.class).abort();
        */
        context.setResult(Result.NOT_BUILT);
        context.onFailure(new AbortException("Aborting flow"));
    }

    /**
     * Records that a flow was canceled while waiting in a stage step because a newer flow entered that stage instead.
     */
    public static final class CanceledCause extends CauseOfInterruption {

        private final String newerBuild;

        CanceledCause(Run<?,?> newerBuild) {
            this.newerBuild = newerBuild.getExternalizableId();
        }

        public Run<?,?> getNewerBuild() {
            return Run.fromExternalizableId(newerBuild);
        }

        @Override public String getShortDescription() {
            return "Superseded by " + getNewerBuild().getDisplayName();
        }

    }

    private static final class Stage {
        /** number of builds current in this stage */
        final Set<Integer> holding = new TreeSet<Integer>();
        /** maximum permitted size of {@link #holding} */
        @CheckForNull
        Integer concurrency;
        /** context of the build currently waiting to enter this stage, if any */
        @CheckForNull StepContext waitingContext;
        /** number of the waiting build, if any */
        @Nullable
        Integer waitingBuild;
        @Override public String toString() {
            return "Stage[holding=" + holding + ",waitingBuild=" + waitingBuild + ",concurrency=" + concurrency + "]";
        }
    }

    @Extension
    public static final class Listener extends RunListener<Run<?,?>> {
        @Override public void onCompleted(Run<?,?> r, TaskListener listener) {
            exit(r);
        }
    }
}
