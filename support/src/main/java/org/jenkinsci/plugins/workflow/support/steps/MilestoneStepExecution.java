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

import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

import com.google.inject.Inject;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Executor;
import hudson.model.InvisibleAction;
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
        if (step.getLabel() != null) {
            node.addAction(new LabelAction(step.getLabel()));
        }
        Integer ordinal = processOrdinal();
        tryToPass(run, getContext(), ordinal);
        return true;
    }

    private synchronized Integer processOrdinal() {
        FlowGraphWalker walker = new FlowGraphWalker();
        walker.addHead(node);
        Integer previousOrdinal = null;
        for (FlowNode n : walker) {
            OrdinalAction a = n.getAction(OrdinalAction.class);
            if (a != null) {
                previousOrdinal = a.ordinal;
                break;
            }
        }
        Integer nextOrdinal = 0;
        if (previousOrdinal != null) {
            nextOrdinal = previousOrdinal + 1;
        }
        node.addAction(new OrdinalAction(nextOrdinal));
        return nextOrdinal;
    }

    private static class OrdinalAction extends InvisibleAction {
        Integer ordinal;
        public OrdinalAction(Integer ordinal) {
            this.ordinal = ordinal;
        }
    }

    // Used in tests only (where the static context is kept between tests somehow)
    @Restricted(DoNotUse.class)
    public static void clear() {
        milestonesByOrdinalByJob = null;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        throw new UnsupportedOperationException();
    }

    private static synchronized void tryToPass(Run<?,?> r, StepContext context, Integer ordinal) {
        LOGGER.log(Level.FINE, "build {0} trying to pass milestone {1}", new Object[] {r, ordinal});
        println(context, "Trying to pass milestone " + ordinal);
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
            milestone = new Milestone(ordinal);
            milestonesInJob.put(ordinal, milestone);
        }

        // Unblock the previous milestone
        for (Map.Entry<Integer, Milestone> entry : milestonesInJob.entrySet()) {
            if (entry.getKey().equals(ordinal)) {
                continue;
            }
            Milestone milestone2 = entry.getValue();
            // The build is passing a milestone, so it's not visible to any previous milestone
            if (milestone2.wentAway(r)) {
                // Ordering check
                if(milestone2.ordinal != ordinal - 1) {
                    throw new MilestoneStepException(
                        String.format("Unordered milestone. Found ordinal %s but %s was expected.", ordinal, milestone2.ordinal + 1));
                }
                // Cancel older builds (holding or waiting to enter)
                cancelOldersInSight(milestone2, r);
            }
        }

        // checking order
        if (milestone.lastBuild != null && r.getNumber() < milestone.lastBuild) {
            // cancel if it's older than the last one passing this milestone
            try {
                cancel(context, milestone.lastBuild, milestone.lastBuildExternalizableId);
            } catch (Exception x) {
                LOGGER.log(WARNING, "could not cancel the current flow", x);
            }
        } else {
            // It's in-order, proceed
            milestone.pass(context, r);
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
            if (milestone.wentAway(r)) {
                modified = true;
                cancelOldersInSight(milestone, r);
            }
        }
        if (modified) {
            cleanUp(job, jobName);
            save();
        }
    }

    /**
     * Cancels any build older than the given one in sight of the milestone.
     *
     * @param r the build which is going away of the given milestone
     * @param milestone the milestone which r is leaving (because it entered the next milestone or finished).
     */
    private static void cancelOldersInSight(Milestone milestone, Run<?, ?> r) {
        // Cancel any older build in sight of the milestone
        for (Integer inSightNumber : milestone.inSight) {
            if (r.getNumber() > inSightNumber) {
                Run<?, ?> olderInSightBuild = r.getParent().getBuildByNumber(inSightNumber);
                Executor e = olderInSightBuild.getExecutor();
                if (e != null) {
                    e.interrupt(Result.NOT_BUILT, new CanceledCause(r.getExternalizableId()));
                } else{
                    LOGGER.log(WARNING, "could not cancel an older flow because it has no assigned executor");
                }
            }
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
            Set<Integer> inSight = entry.getValue().inSight;
            Iterator<Integer> it2 = inSight.iterator();
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
         * Milestone ordinal.
         */
        final Integer ordinal;

        /**
         * Numbers of builds that passed this milestone but haven't passed the next one.
         */
        final Set<Integer> inSight = new TreeSet<Integer>();

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

        Milestone(Integer ordinal) {
            this.ordinal = ordinal;
        }

        @Override public String toString() {
            return "Milestone[inSight=" + inSight + ", lastBuildExternalizableId=" + lastBuildExternalizableId + "]";
        }

        public void pass(StepContext context, Run<?, ?> build) {
            lastBuild = build.getNumber();
            lastBuildExternalizableId = build.getExternalizableId();
            inSight.add(build.getNumber());
            context.onSuccess(null);
        }

        /**
         * Called when a build passes the next milestone.
         *
         * @param build the build passing the next milestone.
         * @return true if the build was in sight (exists in inSight), false otherwise.
         */
        public boolean wentAway(Run<?, ?> build) {
            if (inSight.contains(build.getNumber())) {
                inSight.remove(build.getNumber()); // XSTR-757
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Records that a build was canceled because it reached a milestone but a newer build already passed it, or
     * a newer build {@link Milestone#wentAway(Run)} from the last milestone the build passed.
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
