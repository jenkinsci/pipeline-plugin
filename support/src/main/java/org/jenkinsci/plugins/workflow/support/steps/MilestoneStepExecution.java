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

import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.support.steps.StageStepExecution.CanceledCause;

import com.google.inject.Inject;

import hudson.XmlFile;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

public class MilestoneStepExecution extends AbstractStepExecutionImpl {

    private static final Logger LOGGER = Logger.getLogger(MilestoneStepExecution.class.getName());

    @Inject(optional=true) private transient MilestoneStep step;
    @StepContextParameter private transient Run<?,?> run;
    @StepContextParameter private transient FlowNode node;
    @StepContextParameter private transient TaskListener listener;

    private static Map<String, Map<Integer, Milestone>> milestonesByNameByJob;

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
        Map<Integer, Milestone> milestonesInJob = milestonesByNameByJob.get(jobName);
        if (milestonesInJob == null) {
            milestonesInJob = new TreeMap<Integer,Milestone>();
            milestonesByNameByJob.put(jobName, milestonesInJob);
        }
        Milestone milestone = milestonesInJob.get(ordinal);
        if (milestone == null) {
            milestone = new Milestone();
            milestonesInJob.put(ordinal, milestone);
        }
        milestone.concurrency = concurrency;
        int build = r.number;
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
        milestone.waitingBuild = build;
        milestone.waitingContext = context;
        if (milestone.concurrency == null || milestone.holding.size() < milestone.concurrency) {
            milestone.unblock("Proceeding");
        } else {
            println(context, "Waiting for builds " + milestone.holding);
        }
        cleanUp(job, jobName);
        save();
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

    private static void cleanUp(Job<?,?> job, String jobName) {
        Map<Integer, Milestone> milestonesInJob = milestonesByNameByJob.get(jobName);
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
            if (holding.isEmpty()) {
                assert entry.getValue().waitingContext == null : entry;
                it.remove();
            }
        }
        if (milestonesInJob.isEmpty()) {
            milestonesByNameByJob.remove(jobName);
        }
    }

    @SuppressWarnings("unchecked")
    private static synchronized void load() {
        if (milestonesByNameByJob == null) {
            milestonesByNameByJob = new TreeMap<String, Map<Integer, Milestone>>();
            try {
                XmlFile configFile = getConfigFile();
                if (configFile.exists()) {
                    milestonesByNameByJob = (Map<String, Map<Integer, Milestone>>) configFile.read();
                }
            } catch (IOException x) {
                LOGGER.log(WARNING, null, x);
            }
            LOGGER.log(Level.FINE, "load: {0}", milestonesByNameByJob);
        }
    }

    private static synchronized void save() {
        try {
            getConfigFile().write(milestonesByNameByJob);
        } catch (IOException x) {
            LOGGER.log(WARNING, null, x);
        }
        LOGGER.log(Level.FINE, "save: {0}", milestonesByNameByJob);
    }

    private static XmlFile getConfigFile() throws IOException {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            throw new IOException("Jenkins is not running"); // do not use Jenkins.getActiveInstance() as that is an ISE
        }
        return new XmlFile(new File(j.getRootDir(), MilestoneStep.class.getName() + ".xml"));
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
         * Last build that passed through the milestone, or null if none passed yet.
         */
        @CheckForNull
        Integer lastBuild;

        @Override public String toString() {
            return "Stage[holding=" + holding + ", waitingBuild=" + waitingBuild + ", concurrency=" + concurrency + "]";
        }

        /**
         * Unblocks the build currently waiting.
         *
         * @param message a message to print to the log of the unblocked build
         */
        void unblock(String message) {
            assert Thread.holdsLock(StageStepExecution.class);
            assert waitingContext != null;
            assert waitingBuild != null;
            assert !holding.contains(waitingBuild);
            println(waitingContext, message);
            waitingContext.onSuccess(null);
            holding.add(waitingBuild);
            lastBuild = waitingBuild;
            waitingContext = null;
            waitingBuild = null;
        }
    }

}
