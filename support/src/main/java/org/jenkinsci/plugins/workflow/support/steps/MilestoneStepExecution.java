package org.jenkinsci.plugins.workflow.support.steps;

import static java.util.logging.Level.WARNING;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import com.google.inject.Inject;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import jenkins.model.CauseOfInterruption;
import jenkins.model.Jenkins;

public class MilestoneStepExecution extends AbstractStepExecutionImpl {

    private static final Logger LOGGER = Logger.getLogger(MilestoneStepExecution.class.getName());

    @Inject(optional=true) private transient MilestoneStep step;
    @StepContextParameter private transient Run<?,?> run;
    @StepContextParameter private transient FlowNode node;
    @StepContextParameter private transient TaskListener listener;

    private static Map<String, Map<Integer, Milestone>> milestonesByOrdinalByJob;

    @Override
    public boolean start() throws Exception {
        enter(run, getContext(), step.ordinal, step.concurrency);
        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        throw new UnsupportedOperationException();
    }

    private static synchronized void enter(Run<?,?> r, StepContext context, Integer ordinal, Integer concurrency) {
        LOGGER.log(Level.FINE, "enter {0} milestone {1}", new Object[] {r, ordinal});
        println(context, "Entering milestone " + ordinal);
        load();
        Job<?,?> job = r.getParent();
        String jobName = job.getFullName();
        Map<Integer, Milestone> milestonesInJob = milestonesByOrdinalByJob.get(jobName);
        if (milestonesInJob == null) {
            milestonesInJob = new TreeMap<Integer,Milestone>();
            milestonesByOrdinalByJob.put(jobName, milestonesInJob);
        }
        Milestone milestone = milestonesInJob.get(ordinal);
        if (milestone == null) {
            milestone = new Milestone();
            milestonesInJob.put(ordinal, milestone);
        }
        milestone.concurrency = concurrency;
        int build = r.number;
        String externalizableId = r.getExternalizableId();
        if (milestone.waitingContext != null) {
            // Someone has got to give up.
            if (milestone.waitingBuild < build) {
                // Cancel the older one.
                try {
                    cancel(milestone.waitingContext, context);
                } catch (Exception x) {
                    LOGGER.log(WARNING, "could not cancel an older flow (perhaps since deleted?)", x);
                }
            } else if (milestone.waitingBuild > build) {
                // Cancel this one. And work with the older one below, instead of the one initiating this call.
                try {
                    cancel(context, milestone.waitingContext);
                } catch (Exception x) {
                    LOGGER.log(WARNING, "could not cancel the current flow", x);
                }
                build = milestone.waitingBuild;
                externalizableId = milestone.waitingBuildExternalizableId;
                context = milestone.waitingContext;
            } else {
                throw new IllegalStateException("the same flow is trying to reënter the milestone " + ordinal); // see 'e' with two dots, that's Jesse Glick for you! - KK
            }
        }

        // Unblock the previous milestone
        for (Map.Entry<Integer, Milestone> entry : milestonesInJob.entrySet()) {
            if (entry.getKey().equals(ordinal)) {
                continue;
            }
            Milestone milestone2 = entry.getValue();
            // If we were holding another stage in the same job, release it, unlocking its waiter to proceed.
            if (milestone2.holding.remove(build)) {
                if (milestone2.waitingContext != null) {
                    milestone2.unblock("Unblocked since " + r.getDisplayName() + " is moving into milestone " + ordinal);
                }
            }
        }

        // checking order
        if (milestone.lastBuild != null && build < milestone.lastBuild) {
            // cancel if it's older than the last one passing this milestone
            try {
                cancel(context, milestone.lastBuild, milestone.lastBuildExternalizableId);
            } catch (Exception x) {
                LOGGER.log(WARNING, "could not cancel the current flow", x);
            }
        } else {
            // It's in-order, try to proceed
            milestone.waitingBuild = build;
            milestone.waitingBuildExternalizableId = externalizableId;
            milestone.waitingContext = context;
            if (milestone.concurrency == null || milestone.holding.size() < milestone.concurrency) {
                milestone.unblock("Proceeding");
            } else {
                println(context, "Waiting for builds " + milestone.holding);
            }
        }
        cleanUp(job, jobName);
        save();
    }

    private static synchronized void exit(Run<?,?> r) {
        load();
        LOGGER.log(Level.FINE, "exit {0}: {1}", new Object[] {r, milestonesByOrdinalByJob});
        Job<?,?> job = r.getParent();
        String jobName = job.getFullName();
        Map<Integer, Milestone> milestonesInJob = milestonesByOrdinalByJob.get(jobName);
        if (milestonesInJob == null) {
            return;
        }
        boolean modified = false;
        for (Milestone milestone : milestonesInJob.values()) {
            if (milestone.holding.contains(r.number)) {
                milestone.holding.remove(r.number); // XSTR-757: do not rely on return value of TreeSet.remove(Object)
                modified = true;
                if (milestone.waitingContext != null) {
                    milestone.unblock("Unblocked since " + r.getDisplayName() + " finished");
                }
            }
        }
        if (modified) {
            cleanUp(job, jobName);
            save();
        }
    }

    private static void println(StepContext context, String message) {
        if (!context.isReady()) {
            LOGGER.log(Level.FINE, "cannot print message ‘{0}’ to dead {1}", new Object[] {message, context});
            return;
        }
        try {
            context.get(TaskListener.class).getLogger().println(message);
        } catch (Exception x) {
            LOGGER.log(WARNING, "failed to print message to dead " + context, x);
        }
    }

    // TODO record the stage it got to and display that
    private static void cancel(StepContext context, StepContext newer) throws IOException, InterruptedException {
        if (context.isReady() && newer.isReady()) {
            println(context, "Canceled since " + newer.get(Run.class).getDisplayName() + " got here");
            println(newer, "Canceling older " + context.get(Run.class).getDisplayName());
            context.onFailure(new FlowInterruptedException(Result.NOT_BUILT, new CanceledCause(newer.get(Run.class))));
        } else {
            LOGGER.log(WARNING, "cannot cancel dead {0} or {1}", new Object[] {context, newer});
        }
    }

    private static void cancel(StepContext context, Integer build, String buildExternalizableId) throws IOException, InterruptedException {
        if (context.isReady()) {
            println(context, "Canceled since build #" + build + " already got here");
            context.onFailure(new FlowInterruptedException(Result.NOT_BUILT, new CanceledCause(buildExternalizableId)));
        } else {
            LOGGER.log(WARNING, "cannot cancel dead " + buildExternalizableId);
        }
    }

    private static void cleanUp(Job<?,?> job, String jobName) {
        Map<Integer, Milestone> milestonesInJob = milestonesByOrdinalByJob.get(jobName);
        assert milestonesInJob != null;
        Iterator<Entry<Integer, Milestone>> it = milestonesInJob.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Milestone> entry = it.next();
            Set<Integer> holding = entry.getValue().holding;
            Iterator<Integer> it2 = holding.iterator();
            while (it2.hasNext()) {
                Integer number = it2.next();
                if (job.getBuildByNumber(number) == null) {
                    // Deleted at some point but did not properly clean up from exit(…).
                    LOGGER.log(WARNING, "Cleaning up apparently deleted {0}#{1}", new Object[] {jobName, number});
                    it2.remove();
                }
            }
        }
        // TODO: remove milestones that no longer exist (because the step or the job was deleted)
    }

    @SuppressWarnings("unchecked")
    private static synchronized void load() {
        if (milestonesByOrdinalByJob == null) {
            milestonesByOrdinalByJob = new TreeMap<String, Map<Integer, Milestone>>();
            try {
                XmlFile configFile = getConfigFile();
                if (configFile.exists()) {
                    milestonesByOrdinalByJob = (Map<String, Map<Integer, Milestone>>) configFile.read();
                }
            } catch (IOException x) {
                LOGGER.log(WARNING, null, x);
            }
            LOGGER.log(Level.FINE, "load: {0}", milestonesByOrdinalByJob);
        }
    }

    private static synchronized void save() {
        try {
            getConfigFile().write(milestonesByOrdinalByJob);
        } catch (IOException x) {
            LOGGER.log(WARNING, null, x);
        }
        LOGGER.log(Level.FINE, "save: {0}", milestonesByOrdinalByJob);
    }

    private static XmlFile getConfigFile() throws IOException {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            throw new IOException("Jenkins is not running"); // do not use Jenkins.getActiveInstance() as that is an ISE
        }
        return new XmlFile(new File(j.getRootDir(), MilestoneStep.class.getName() + ".xml"));
    }

    @Extension
    public static final class Listener extends RunListener<Run<?,?>> {
        @Override public void onCompleted(Run<?,?> r, TaskListener listener) {
            if (!(r instanceof FlowExecutionOwner.Executable) || ((FlowExecutionOwner.Executable) r).asFlowExecutionOwner() == null) {
                return;
            }
            exit(r);
        }
    }

    private static final class Milestone {

        /**
         * Numbers of builds currently running in this milestone.
         */
        final Set<Integer> holding = new TreeSet<Integer>();

        /**
         * Maximum permitted size of {@link #holding}, or null for unbounded.
         */
        @CheckForNull
        Integer concurrency;

        /**
         * Context of the build currently waiting to enter this stage, if any.
         */
        @CheckForNull StepContext waitingContext;

        /**
         * Number of the build corresponding to {@link #waitingContext}, if any.
         */
        @Nullable
        Integer waitingBuild;

        /**
         * Externalizable ID of the build corresponding to {@link #waitingContext}, if any.
         */
        @Nullable
        String waitingBuildExternalizableId;

        /**
         * Last build that passed through the milestone, or null if none passed yet.
         */
        @CheckForNull
        Integer lastBuild;

        /**
         * Last build extenalizable ID hat passed through the milestone, or null if none passed yet.
         */
        @CheckForNull
        String lastBuildExternalizableId;

        @Override public String toString() {
            return "Stage[holding=" + holding + ", waitingBuild=" + waitingBuild + ", concurrency=" + concurrency + "]";
        }

        /**
         * Unblocks the build currently waiting.
         *
         * @param message a message to print to the log of the unblocked build
         */
        void unblock(String message) {
            assert Thread.holdsLock(MilestoneStepExecution.class);
            assert waitingContext != null;
            assert waitingBuild != null;
            assert !holding.contains(waitingBuild);
            println(waitingContext, message);
            waitingContext.onSuccess(null);
            holding.add(waitingBuild);
            lastBuild = waitingBuild;
            lastBuildExternalizableId = waitingBuildExternalizableId;
            waitingContext = null;
            waitingBuild = null;
            waitingBuildExternalizableId = null;
        }
    }

    /**
     * Records that a flow was canceled while waiting in a stage step because a newer flow entered that stage instead.
     */
    public static final class CanceledCause extends CauseOfInterruption {

        private static final long serialVersionUID = 1;

        private final String newerBuild;

        CanceledCause(Run<?,?> newerBuild) {
            this.newerBuild = newerBuild.getExternalizableId();
        }

        CanceledCause(String newerBuild) {
            this.newerBuild = newerBuild;
        }

        public Run<?,?> getNewerBuild() {
            return Run.fromExternalizableId(newerBuild);
        }

        @Override public String getShortDescription() {
            return "Superseded by " + getNewerBuild().getDisplayName();
        }

    }

    private static final long serialVersionUID = 1L;

}
