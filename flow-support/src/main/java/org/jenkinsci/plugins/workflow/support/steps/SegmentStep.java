/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package org.jenkinsci.plugins.workflow.support.steps;

import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Marks a flow build as entering a gated “segment”, like a stage in a pipeline.
 * Each job has a set of named segments, each of which acts like a semaphore with an initial permit count,
 * but with the special behavior that only one build may be waiting at any time: the newest.
 * Credit goes to @jtnord for implementing the {@code block} operator in {@code buildflow-extensions}, which inspired this.
 */
public class SegmentStep extends Step {
    
    private static final Logger LOGGER = Logger.getLogger(SegmentStep.class.getName());

    public final String name;
    private final @CheckForNull Integer concurrency;

    private SegmentStep(String name, @CheckForNull Integer concurrency) {
        if (name == null) {
            throw new IllegalArgumentException("must specify name");
        }
        this.name = name;
        this.concurrency = concurrency;
    }

    @DataBoundConstructor public SegmentStep(String name, String concurrency) {
        this(Util.fixEmpty(name), Util.fixEmpty(concurrency) != null ? Integer.parseInt(concurrency) : null);
    }

    public String getConcurrency() {
        return concurrency == null ? "" : Integer.toString(concurrency);
    }

    @Override public boolean start(StepContext context) throws Exception {
        FlowNode n = context.get(FlowNode.class);
        n.addAction(new LabelAction(name));
        Run<?,?> r = context.get(Run.class);
        enter(r, context, name, concurrency);
        return false;
    }

    private static XmlFile getConfigFile() {
        return new XmlFile(new File(Jenkins.getInstance().getRootDir(), SegmentStep.class.getName() + ".xml"));
    }

    private static Map<String,Map<String,Segment>> segmentsByNameByJob;

    // TODO or delete and make this an instance field in DescriptorImpl
    public static void clear() {
        segmentsByNameByJob = null;
    }

    @SuppressWarnings("unchecked")
    private static synchronized void load() {
        if (segmentsByNameByJob == null) {
            segmentsByNameByJob = new TreeMap<String,Map<String,Segment>>();
            XmlFile configFile = getConfigFile();
            if (configFile.exists()) {
                try {
                    segmentsByNameByJob = (Map<String,Map<String,Segment>>) configFile.read();
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, null, x);
                }
            }
            LOGGER.log(Level.FINE, "load: {0}", segmentsByNameByJob);
        }
    }

    private static synchronized void save() {
        try {
            getConfigFile().write(segmentsByNameByJob);
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
        }
        LOGGER.log(Level.FINE, "save: {0}", segmentsByNameByJob);
    }

    private static synchronized void enter(Run<?,?> r, StepContext context, String name, Integer concurrency) {
        LOGGER.log(Level.FINE, "enter {0} {1}", new Object[] {r, name});
        println(context, "Entering segment " + name);
        load();
        Job<?,?> job = r.getParent();
        String jobName = job.getFullName();
        Map<String,Segment> segmentsByName = segmentsByNameByJob.get(jobName);
        if (segmentsByName == null) {
            segmentsByName = new TreeMap<String,Segment>();
            segmentsByNameByJob.put(jobName, segmentsByName);
        }
        Segment segment = segmentsByName.get(name);
        if (segment == null) {
            segment = new Segment();
            segmentsByName.put(name, segment);
        }
        segment.concurrency = concurrency;
        int build = r.number;
        if (segment.waitingContext != null) {
            // Someone has got to give up.
            if (segment.waitingBuild < build) {
                // Cancel the older one.
                try {
                    cancel(segment.waitingContext, context);
                } catch (Exception x) {
                    LOGGER.log(Level.WARNING, "could not cancel an older flow (perhaps since deleted?)", x);
                }
            } else if (segment.waitingBuild > build) {
                // Cancel this one. And work with the older one below, instead of the one initiating this call.
                try {
                    cancel(context, segment.waitingContext);
                } catch (Exception x) {
                    LOGGER.log(Level.WARNING, "could not cancel the current flow", x);
                }
                build = segment.waitingBuild;
                context = segment.waitingContext;
            } else {
                throw new IllegalStateException("the same flow is trying to reënter the segment " + name);
            }
        }
        for (Map.Entry<String,Segment> entry : segmentsByName.entrySet()) {
            if (entry.getKey().equals(name)) {
                continue;
            }
            Segment segment2 = entry.getValue();
            // If we were holding another segment in the same job, release it, unlocking its waiter to proceed.
            if (segment2.holding.remove(build)) {
                if (segment2.waitingContext != null) {
                    println(segment2.waitingContext, "Unblocked since " + r.getDisplayName() + " is moving into segment " + name);
                    segment2.waitingContext.onSuccess(null);
                    segment2.waitingBuild = null;
                    segment2.waitingContext = null;
                }
            }
        }
        if (segment.concurrency == null || segment.holding.size() < segment.concurrency) {
            segment.waitingBuild = null;
            segment.waitingContext = null;
            segment.holding.add(build);
            println(context, "Proceeding");
            context.onSuccess(null);
        } else {
            segment.waitingBuild = build;
            segment.waitingContext = context;
            println(context, "Waiting for builds " + segment.holding);
        }
        cleanUp(jobName);
        save();
    }

    private static synchronized void exit(Run<?,?> r) {
        load();
        LOGGER.log(Level.FINE, "exit {0}: {1}", new Object[] {r, segmentsByNameByJob});
        Job<?,?> job = r.getParent();
        String jobName = job.getFullName();
        Map<String,Segment> segmentsByName = segmentsByNameByJob.get(jobName);
        if (segmentsByName == null) {
            return;
        }
        boolean modified = false;
        for (Segment segment : segmentsByName.values()) {
            if (segment.holding.contains(r.number)) {
                segment.holding.remove(r.number); // XSTR-757: do not rely on return value of TreeSet.remove(Object)
                modified = true;
                if (segment.waitingContext != null) {
                    println(segment.waitingContext, "Unblocked since " + r.getDisplayName() + " finished");
                    segment.waitingContext.onSuccess(null);
                    segment.waitingContext = null;
                    segment.waitingBuild = null;
                }
            }
        }
        if (modified) {
            cleanUp(jobName);
            save();
        }
    }

    private static void cleanUp(String jobName) {
        Map<String,Segment> segmentsByName = segmentsByNameByJob.get(jobName);
        assert segmentsByName != null;
        Iterator<Map.Entry<String,Segment>> it = segmentsByName.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String,Segment> entry = it.next();
            if (entry.getValue().holding.isEmpty()) {
                assert entry.getValue().waitingContext == null;
                it.remove();
            }
        }
        if (segmentsByName.isEmpty()) {
            segmentsByNameByJob.remove(jobName);
        }
    }

    private static void println(StepContext context, String message) {
        try {
            context.get(TaskListener.class).getLogger().println(message);
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, null, x);
        }
    }

    // TODO record the segment it got to and display that
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
     * Records that a flow was canceled while waiting in a segment step because a newer flow entered that segment instead.
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

    private static final class Segment {
        /** number of builds current in this segment */
        final Set<Integer> holding = new TreeSet<Integer>();
        /** maximum permitted size of {@link #holding} */
        @CheckForNull Integer concurrency;
        /** context of the build currently waiting to enter this segment, if any */
        @CheckForNull StepContext waitingContext;
        /** number of the waiting build, if any */
        @Nullable Integer waitingBuild;
        @Override public String toString() {
            return "Segment[holding=" + holding + ",waitingBuild=" + waitingBuild + ",concurrency=" + concurrency + "]";
        }
    }

    @Extension public static final class Listener extends RunListener<Run<?,?>> {
        @Override public void onCompleted(Run<?,?> r, TaskListener listener) {
            exit(r);
        }
    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public Set<Class<?>> getRequiredContext() {
            Set<Class<?>> r = new HashSet<Class<?>>();
            r.add(Run.class);
            r.add(FlowExecution.class);
            r.add(FlowNode.class);
            r.add(TaskListener.class);
            return r;
        }

        @Override public String getFunctionName() {
            return "segment";
        }

        @Override public Step newInstance(Map<String,Object> arguments) {
            return new SegmentStep((String) arguments.get("value"), (Integer) arguments.get("concurrency"));
        }

        @Override public String getDisplayName() {
            return "Segment";
        }

    }

}
