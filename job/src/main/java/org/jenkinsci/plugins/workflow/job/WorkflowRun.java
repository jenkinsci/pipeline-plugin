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

import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import hudson.EnvVars;
import hudson.XmlFile;
import hudson.console.AnnotatedLargeText;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.util.NullStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import jenkins.model.Jenkins;
import jenkins.model.lazy.BuildReference;
import jenkins.model.lazy.LazyBuildMixIn;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;

@SuppressWarnings("SynchronizeOnNonFinalField")
public final class WorkflowRun extends Run<WorkflowJob,WorkflowRun> implements Queue.Executable, LazyBuildMixIn.LazyLoadingRun<WorkflowJob,WorkflowRun> {

    private static final Logger LOGGER = Logger.getLogger(WorkflowRun.class.getName());

    /** null until started */
    private @Nullable FlowExecution execution;

    /**
     * {@link Future} that yields {@link #execution}, when it is fully configured and ready to be exposed.
     */
    private transient SettableFuture<FlowExecution> executionPromise = SettableFuture.create();

    private transient final LazyBuildMixIn.RunMixIn<WorkflowJob,WorkflowRun> runMixIn = new LazyBuildMixIn.RunMixIn<WorkflowJob,WorkflowRun>() {
        @Override protected WorkflowRun asRun() {
            return WorkflowRun.this;
        }
    };
    private transient StreamBuildListener listener;
    private transient Object completionLock;
    /** map from node IDs to log positions from which we should copy text */
    private Map<String,Long> logsToCopy;

    public WorkflowRun(WorkflowJob job) throws IOException {
        super(job);
        //System.err.printf("created %s @%h%n", this, this);
    }

    public WorkflowRun(WorkflowJob job, File dir) throws IOException {
        super(job, dir);
        //System.err.printf("loaded %s @%h%n", this, this);
    }

    @Override public LazyBuildMixIn.RunMixIn<WorkflowJob,WorkflowRun> getRunMixIn() {
        return runMixIn;
    }

    @Override protected BuildReference<WorkflowRun> createReference() {
        return getRunMixIn().createReference();
    }

    @Override protected void dropLinks() {
        getRunMixIn().dropLinks();
    }

    @Override public WorkflowRun getPreviousBuild() {
        return getRunMixIn().getPreviousBuild();
    }

    @Override public WorkflowRun getNextBuild() {
        return getRunMixIn().getNextBuild();
    }

    /**
     * Exposed to URL space via Stapler.
     */
    public FlowGraphTable getFlowGraph() {
        FlowGraphTable t = new FlowGraphTable(getExecution());
        t.build();
        return t;
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings({"ML_SYNC_ON_UPDATED_FIELD", "UW_UNCOND_WAIT"})
    @Override public void run() {
        // TODO how to set startTime? reflection?
        // TODO lots here copied from execute(RunExecution)
        try {
            OutputStream logger = new FileOutputStream(getLogFile());
            listener = new StreamBuildListener(logger, Charset.defaultCharset());
            listener.started(getCauses());
            RunListener.fireStarted(this, listener);
            updateSymlinks(listener);
            FlowDefinition definition = getParent().getDefinition();
            if (definition == null) {
                listener.error("No flow definition, cannot run");
                return;
            }
            execution = definition.create(new Owner(this), getAllActions());
            execution.addListener(new GraphL());
            completionLock = new Object();
            logsToCopy = new LinkedHashMap<String,Long>();
            execution.start();
            executionPromise.set(execution);
            while (!execution.isComplete()) {
                synchronized (completionLock) {
                    completionLock.wait(1000);
                    copyLogs();
                }
            }
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, null, x);
            result = Result.FAILURE;
            executionPromise.setException(x);
        }
    }

    @GuardedBy("completionLock")
    private void copyLogs() {
        if (logsToCopy == null) { // finished
            return;
        }
        Iterator<Map.Entry<String,Long>> it = logsToCopy.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String,Long> entry = it.next();
            FlowNode node;
            try {
                node = execution.getNode(entry.getKey());
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, null, x);
                it.remove();
                continue;
            }
            if (node == null) {
                LOGGER.log(Level.WARNING, "no such node {0}", entry.getKey());
                it.remove();
                continue;
            }
            LogAction la = node.getAction(LogAction.class);
            if (la != null) {
                AnnotatedLargeText<? extends FlowNode> logText = la.getLogText();
                try {
                    entry.setValue(logText.writeLogTo(entry.getValue(), listener.getLogger()));
                    if (logText.isComplete()) { // currently same as !entry.getKey().isRunning()
                        logText.writeLogTo(entry.getValue(), listener.getLogger()); // defend against race condition?
                        // TODO !CpsFlowExecution.getNode(id).running even after it is done, since running is deserialized as false!
                        //it.remove();
                    }
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, null, x);
                    it.remove();
                }
                /* TODO see above
            } else if (!node.isRunning()) {
                it.remove();
                */
            }
        }
    }
    
    private static final Map<String,WorkflowRun> LOADING_RUNS = new HashMap<String,WorkflowRun>();

    private String key() {
        return getParent().getFullName() + '/' + getId();
    }

    /** Hack to allow {@link #execution} to use an {@link Owner} referring to this run, even when it has not yet been loaded. */
    @Override public void reload() throws IOException {
        LOADING_RUNS.put(key(), this);
        super.reload();
    }

    @Override protected void onLoad() {
        super.onLoad();
        if (execution != null) {
            execution.onLoad();
            execution.addListener(new GraphL());
            if (!execution.isComplete()) {
                try {
                    OutputStream logger = new FileOutputStream(getLogFile(), true);
                    listener = new StreamBuildListener(logger, Charset.defaultCharset());
                    listener.getLogger().println("Resuming build");
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, null, x);
                    listener = new StreamBuildListener(new NullStream());
                }
                completionLock = new Object();
                // TODO should again schedule something from the WorkflowJob (just be careful that createExecutable does not create a new build), and wait on completionLock
            }
            executionPromise.set(execution);
        }
        LOADING_RUNS.remove(key()); // or could just make the value type be WeakReference<WorkflowRun>
    }

    private void finish(Result r) {
        result = r;
        // TODO set duration
        RunListener.fireCompleted(WorkflowRun.this, listener);
        Throwable t = execution.getCauseOfFailure();
        if (t != null) {
            t.printStackTrace(listener.getLogger());
        }
        listener.finished(result);
        listener.closeQuietly();
        logsToCopy = null;
        try {
            save();
            getParent().logRotate();
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, null, x);
        }
        RunListener.fireFinalized(this);
        assert completionLock != null;
        synchronized (completionLock) {
            completionLock.notifyAll();
        }
    }

    /**
     * Gets the associated execution state.
     * @return non-null after the flow has started, even after finished (but may be null temporarily when about to start, or if starting failed)
     */
    public @CheckForNull FlowExecution getExecution() {
        return execution;
    }

    /**
     * Allows the caller to block on {@link FlowExecution}, which gets created relatively quickly
     * after the build gets going.
     */
    public ListenableFuture<FlowExecution> getExecutionPromise() {
        return executionPromise;
    }

    /* TODO: is Stapler-binding nodes useful?
    public FlowNode getNode(String id) throws IOException {
        return execution == null ? null : execution.getNode(id);
    }
    */

    @Override
    public boolean hasntStartedYet() {
        return execution==null;
    }

    @Override public boolean isBuilding() {
        return execution == null || !execution.isComplete();
    }

    @Override protected boolean isInProgress() {
        return execution != null && !execution.isComplete();
    }

    @Override public boolean isLogUpdated() {
        return isBuilding(); // there is no equivalent to a post-production state for flows
    }

    @Override public EnvVars getEnvironment(TaskListener listener) throws IOException, InterruptedException {
        EnvVars env = super.getEnvironment(listener);
        // TODO of what use is this? Should it rather be inlined into FlowExecution constructors?
        ParametersAction a = getAction(ParametersAction.class);
        if (a != null) {
            for (ParameterValue v : a) {
                v.buildEnvironment(this, env);
            }
        }
        EnvVars.resolve(env);
        return env;
    }

    private static final class Owner extends FlowExecutionOwner {
        private final String job;
        private final String id;
        private transient WorkflowRun run;
        Owner(WorkflowRun run) {
            job = run.getParent().getFullName();
            id = run.getId();
        }
        private @Nonnull WorkflowRun run() throws IOException {
            if (run==null) {
                WorkflowRun candidate = LOADING_RUNS.get(job + '/' + id);
                if (candidate != null && candidate.getParent().getFullName().equals(job) && candidate.getId().equals(id)) {
                    run = candidate;
                } else {
                    WorkflowJob j = Jenkins.getInstance().getItemByFullName(job, WorkflowJob.class);
                    if (j == null) {
                        throw new IOException("no such WorkflowJob " + job);
                    }
                    run = j._getRuns().getById(id);
                    if (run == null) {
                        throw new IOException("no such build " + id + " in " + job);
                    }
                    //System.err.printf("getById found %s @%h%n", run, run);
                }
            }
            return run;
        }
        @Override public FlowExecution get() throws IOException {
            WorkflowRun r = run();
            FlowExecution exec = r.execution;
            if (exec != null) {
                return exec;
            } else {
                throw new IOException(r + " did not yet start");
            }
        }
        @Override public File getRootDir() throws IOException {
            return run().getRootDir();
        }
        @Override public Queue.Executable getExecutable() throws IOException {
            return run();
        }
        @Override
        public PrintStream getConsole() {
            try {
                return run().listener.getLogger();
            } catch (IOException e) {
                return System.out;  // fallback
            }
        }
    }

    private final class GraphL implements GraphListener {
        @Override public void onNewHead(FlowNode node) {
            synchronized (completionLock) {
                copyLogs();
                logsToCopy.put(node.getId(), 0L);
            }
            listener.getLogger().println("Running: " + node.getDisplayName());
            if (node instanceof FlowEndNode) {
                finish(((FlowEndNode) node).getResult());
            } else {
                try {
                    save();
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, null, x);
                }
            }
        }
    }

    static void alias() {
        Run.XSTREAM2.alias("flow-build", WorkflowRun.class);
        new XmlFile(null).getXStream().aliasType("flow-owner", Owner.class); // hack! but how else to set it for arbitrary Descriptor’s?
        Run.XSTREAM2.aliasType("flow-owner", Owner.class);
    }
}
