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

import hudson.console.LineTransformationOutputStream;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Main;
import hudson.XmlFile;
import hudson.console.AnnotatedLargeText;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.RunMap;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import hudson.util.NullStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import jenkins.model.Jenkins;
import jenkins.model.lazy.BuildReference;
import jenkins.model.lazy.LazyBuildMixIn;
import jenkins.model.queue.AsynchronousExecution;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;

@SuppressWarnings("SynchronizeOnNonFinalField")
@edu.umd.cs.findbugs.annotations.SuppressWarnings("JLM_JSR166_UTILCONCURRENT_MONITORENTER") // completed is an unusual usage
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
    private transient AtomicBoolean completed;
    /** map from node IDs to log positions from which we should copy text */
    private Map<String,Long> logsToCopy;

    List<SCMCheckout> checkouts;
    // TODO could use a WeakReference to reduce memory, but that complicates how we add to it incrementally; perhaps keep a List<WeakReference<ChangeLogSet<?>>>
    private transient List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets;

    /** True when first started, false when running after a restart. */
    private transient boolean firstTime;

    public WorkflowRun(WorkflowJob job) throws IOException {
        super(job);
        firstTime = true;
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

    @Exported
    @Override public WorkflowRun getPreviousBuild() {
        return getRunMixIn().getPreviousBuild();
    }

    @Exported
    @Override public WorkflowRun getNextBuild() {
        return getRunMixIn().getNextBuild();
    }

    /**
     * Actually executes the workflow.
     */
    @Override public void run() {
        if (!firstTime) {
            throw sleep();
        }
        // Some code here copied from execute(RunExecution), but subsequently modified quite a bit.
        try {
            onStartBuilding();
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
            checkouts = new LinkedList<SCMCheckout>();
            Owner owner = new Owner(this);
            
            FlowExecution newExecution = definition.create(owner, listener, getAllActions());
            FlowExecutionList.get().register(owner);
            newExecution.addListener(new GraphL());
            completed = new AtomicBoolean();
            logsToCopy = new LinkedHashMap<String,Long>();
            newExecution.start();
            
            // It's only okay to initialise the global once the new FlowExecution
            // has successfully started.
            execution = newExecution;
            executionPromise.set(newExecution);
        } catch (Throwable x) {
            if (listener == null) {
                LOGGER.log(Level.WARNING, this + " failed to start", x);
            } else {
                x.printStackTrace(listener.error("failed to start build"));
            }
            setResult(Result.FAILURE);
            executionPromise.setException(x);
            return;
        }
        throw sleep();
    }

    private AsynchronousExecution sleep() {
        final AsynchronousExecution asynchronousExecution = new AsynchronousExecution() {
            @Override public void interrupt(boolean forShutdown) {
                if (forShutdown) {
                    return;
                }
                try {
                    execution.interrupt(Result.ABORTED);
                } catch (Exception x) {
                    LOGGER.log(Level.WARNING, null, x);
                }
                getExecutor().recordCauseOfInterruption(WorkflowRun.this, listener);
            }
            @Override public boolean blocksRestart() {
                return execution.blocksRestart();
            }
            @Override public boolean displayCell() {
                return blocksRestart();
            }
        };
        final AtomicReference<ScheduledFuture<?>> copyLogsTask = new AtomicReference<ScheduledFuture<?>>();
        copyLogsTask.set(Timer.get().scheduleAtFixedRate(new Runnable() {
            @Override public void run() {
                synchronized (completed) {
                    if (completed.get()) {
                        asynchronousExecution.completed(null);
                        copyLogsTask.get().cancel(false);
                        return;
                    }
                    Jenkins jenkins = Jenkins.getInstance();
                    if (jenkins == null || jenkins.isTerminating()) {
                        LOGGER.log(Level.FINE, "shutting down, breaking waitForCompletion on {0}", this);
                        // Stop writing content, in case a new set of objects gets loaded after in-VM restart and starts writing to the same file:
                        listener.closeQuietly();
                        listener = new StreamBuildListener(new NullStream());
                        return;
                    }
                    copyLogs();
                }
            }
        }, 1, 1, TimeUnit.SECONDS));
        return asynchronousExecution;
    }

    @GuardedBy("completed")
    private void copyLogs() {
        if (logsToCopy == null) { // finished
            return;
        }
        Iterator<Map.Entry<String,Long>> it = logsToCopy.entrySet().iterator();
        boolean modified = false;
        while (it.hasNext()) {
            Map.Entry<String,Long> entry = it.next();
            FlowNode node;
            try {
                node = execution.getNode(entry.getKey());
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, null, x);
                it.remove();
                modified = true;
                continue;
            }
            if (node == null) {
                LOGGER.log(Level.WARNING, "no such node {0}", entry.getKey());
                it.remove();
                modified = true;
                continue;
            }
            LogAction la = node.getAction(LogAction.class);
            if (la != null) {
                AnnotatedLargeText<? extends FlowNode> logText = la.getLogText();
                try {
                    long old = entry.getValue();
                    OutputStream logger;

                    String prefix = getLogPrefix(node);
                    if (prefix != null) {
                        logger = new LogLinePrefixOutputFilter(listener.getLogger(), "[" + prefix + "] ");
                    } else {
                        logger = listener.getLogger();
                    }

                    try {
                        long revised = logText.writeRawLogTo(old, logger);
                        if (revised != old) {
                            entry.setValue(revised);
                            modified = true;
                        }
                        if (logText.isComplete()) {
                            logText.writeRawLogTo(entry.getValue(), logger); // defend against race condition?
                            assert !node.isRunning() : "LargeText.complete yet " + node + " claims to still be running";
                            it.remove();
                            modified = true;
                        }
                    } finally {
                        if (prefix != null) {
                            ((LogLinePrefixOutputFilter)logger).forceEol();
                        }
                    }
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, null, x);
                    it.remove();
                    modified = true;
                }
            } else if (!node.isRunning()) {
                it.remove();
                modified = true;
            }
        }
        if (modified) {
            try {
                save();
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, null, x);
            }
        }
    }

    private String getLogPrefix(FlowNode node) {
        if (node instanceof BlockEndNode) {
            return null;
        }

        ThreadNameAction threadNameAction = node.getAction(ThreadNameAction.class);

        if (threadNameAction != null) {
            return threadNameAction.getThreadName();
        }

        for (FlowNode parent : node.getParents()) {
            String prefix = getLogPrefix(parent);
            if (prefix != null) {
                return prefix;
            }
        }

        return null;
    }

    private static final class LogLinePrefixOutputFilter extends LineTransformationOutputStream {

        private final PrintStream logger;
        private final String prefix;

        protected LogLinePrefixOutputFilter(PrintStream logger, String prefix) {
            this.logger = logger;
            this.prefix = prefix;
        }

        @Override
        protected void eol(byte[] b, int len) throws IOException {
            logger.append(prefix);
            logger.write(b, 0, len);
        }
    }
    
    private static final Map<String,WorkflowRun> LOADING_RUNS = new HashMap<String,WorkflowRun>();

    /**
     * Same as {@link Run#getId} except it works before the run has been loaded from disk.
     * TODO JENKINS-27531 this logic should be handled directly in Run.getId() instead.
     */
    private static String getId(WorkflowRun r) {
        String id = r.getId();
        Class<?> runIdMigratorC;
        try {
            runIdMigratorC = Class.forName("jenkins.model.RunIdMigrator");
        } catch (ClassNotFoundException x) {
            // 1.596 or earlier, so the ID is fine.
            return id;
        }
        try {
            RunMap<WorkflowRun> runMap = r.getParent()._getRuns();
            Field runIdMigratorF = RunMap.class.getField("runIdMigrator");
            Object runIdMigratorO = runIdMigratorF.get(runMap);
            Field idToNumberF = runIdMigratorC.getDeclaredField("idToNumber");
            idToNumberF.setAccessible(true);
            Map<String,Integer> idToNumberO = (Map<String,Integer>) idToNumberF.get(runIdMigratorO);
            int n = r.getNumber();
            for (Map.Entry<String,Integer> entry : idToNumberO.entrySet()) {
                if (entry.getValue().equals(n)) {
                    id = entry.getKey();
                    LOGGER.log(Level.FINE, "recovered legacy ID {0} for {1}", new Object[] {id, r});
                    return id;
                }
            }
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, null, x);
        }
        return id;
    }

    private String key() {
        return getParent().getFullName() + '/' + getId(this);
    }

    /** Hack to allow {@link #execution} to use an {@link Owner} referring to this run, even when it has not yet been loaded. */
    @Override public void reload() throws IOException {
        synchronized (LOADING_RUNS) {
            LOADING_RUNS.put(key(), this);
        }

        // super.reload() forces result to be FAILURE, so working around that
        new XmlFile(XSTREAM,new File(getRootDir(),"build.xml")).unmarshal(this);
    }

    @Override protected void onLoad() {
        super.onLoad();
        if (completed != null) {
            throw new IllegalStateException("double onLoad of " + this);
        } else if (Main.isUnitTest) {
            System.err.printf("loading %s @%h%n", this, this);
        }
        if (execution != null) {
            execution.onLoad();
            execution.addListener(new GraphL());
            executionPromise.set(execution);
            if (!execution.isComplete()) {
                // we've been restarted while we were running. let's get the execution going again.
                try {
                    OutputStream logger = new FileOutputStream(getLogFile(), true);
                    listener = new StreamBuildListener(logger, Charset.defaultCharset());
                    listener.getLogger().println("Resuming build");
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, null, x);
                    listener = new StreamBuildListener(new NullStream());
                }
                completed = new AtomicBoolean();
                Queue.getInstance().schedule(new AfterRestartTask(this), 0);
            }
        }
        synchronized (LOADING_RUNS) {
            LOADING_RUNS.remove(key()); // or could just make the value type be WeakReference<WorkflowRun>
            LOADING_RUNS.notifyAll();
        }
    }

    // Overridden since super version has an unwanted assertion about this.state, which we do not use.
    @Override public void setResult(Result r) {
        if (result == null || r.isWorseThan(result)) {
            result = r;
            LOGGER.log(Level.FINE, this + " in " + getRootDir() + ": result is set to " + r, LOGGER.isLoggable(Level.FINER) ? new Exception() : null);
        }
    }

    private void finish(Result r) {
        setResult(r);
        LOGGER.log(Level.INFO, "{0} completed: {1}", new Object[] {this, getResult()});
        // TODO set duration
        RunListener.fireCompleted(WorkflowRun.this, listener);
        Throwable t = execution.getCauseOfFailure();
        if (t instanceof AbortException) {
            listener.error(t.getMessage());
        } else if (t instanceof FlowInterruptedException) {
            ((FlowInterruptedException) t).handle(this, listener);
        } else if (t != null) {
            t.printStackTrace(listener.getLogger());
        }
        listener.finished(getResult());
        listener.closeQuietly();
        logsToCopy = null;
        duration = Math.max(0, System.currentTimeMillis() - getStartTimeInMillis());
        try {
            save();
            getParent().logRotate();
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, null, x);
        }
        onEndBuilding();
        assert completed != null;
        synchronized (completed) {
            completed.set(true);
        }
        FlowExecutionList.get().unregister(execution.getOwner());
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

    @Override
    public boolean hasntStartedYet() {
        return result == null && execution==null;
    }

    @Override public boolean isBuilding() {
        return result == null || isInProgress();
    }

    @Exported
    @Override protected boolean isInProgress() {
        return execution != null && !execution.isComplete();
    }

    @Override public boolean isLogUpdated() {
        return isBuilding(); // there is no equivalent to a post-production state for flows
    }

    @Exported
    public synchronized List<ChangeLogSet<? extends ChangeLogSet.Entry>> getChangeSets() {
        if (changeSets == null) {
            changeSets = new ArrayList<ChangeLogSet<? extends ChangeLogSet.Entry>>();
            if (checkouts == null) {
                LOGGER.log(Level.WARNING, "no checkouts in {0}", this);
                return changeSets;
            }
            for (SCMCheckout co : checkouts) {
                if (co.changelogFile != null && co.changelogFile.isFile()) {
                    try {
                        changeSets.add(co.scm.createChangeLogParser().parse(this, co.scm.getEffectiveBrowser(), co.changelogFile));
                    } catch (Exception x) {
                        LOGGER.log(Level.WARNING, "could not parse " + co.changelogFile, x);
                    }
                }
            }
        }
        return changeSets;
    }

    @RequirePOST
    public synchronized HttpResponse doStop() {
        Executor e = getOneOffExecutor();
        if (e != null) {
            return e.doStop();
        } else {
            return HttpResponses.forwardToPreviousPage();
        }
    }

    private void onCheckout(SCM scm, FilePath workspace, @CheckForNull File changelogFile, @CheckForNull SCMRevisionState pollingBaseline) throws Exception {
        if (changelogFile != null && changelogFile.isFile()) {
            ChangeLogSet<?> cls = scm.createChangeLogParser().parse(this, scm.getEffectiveBrowser(), changelogFile);
            getChangeSets().add(cls);
            for (SCMListener l : SCMListener.all()) {
                l.onChangeLogParsed(this, scm, listener, cls);
            }
        }
        // TODO JENKINS-26096 prefer a variant returning only Computer.name even if offline
        Computer computer = workspace.toComputer();
        if (computer == null) {
            throw new IllegalStateException();
        }
        checkouts.add(new SCMCheckout(scm, computer.getName(), workspace.getRemote(), changelogFile, pollingBaseline));
    }

    static final class SCMCheckout {
        final SCM scm;
        final String node;
        final String workspace;
        // TODO make this a String and relativize to Run.rootDir if possible
        final @CheckForNull File changelogFile;
        final @CheckForNull SCMRevisionState pollingBaseline;
        SCMCheckout(SCM scm, String node, String workspace, File changelogFile, SCMRevisionState pollingBaseline) {
            this.scm = scm;
            this.node = node;
            this.workspace = workspace;
            this.changelogFile = changelogFile;
            this.pollingBaseline = pollingBaseline;
        }
    }

    private static final class Owner extends FlowExecutionOwner {
        private final String job;
        private final String id;
        private transient @CheckForNull WorkflowRun run;
        Owner(WorkflowRun run) {
            job = run.getParent().getFullName();
            id = run.getId();
            this.run = run;
        }
        private String key() {
            return job + '/' + id;
        }
        private @Nonnull WorkflowRun run() throws IOException {
            if (run==null) {
                WorkflowRun candidate;
                synchronized (LOADING_RUNS) {
                    candidate = LOADING_RUNS.get(key());
                }
                if (candidate != null && candidate.getParent().getFullName().equals(job) && getId(candidate).equals(id)) {
                    run = candidate;
                } else {
                    Jenkins jenkins = Jenkins.getInstance();
                    if (jenkins == null) {
                        throw new IOException("Jenkins is not running"); // do not use Jenkins.getActiveInstance() as that is an ISE
                    }
                    WorkflowJob j = jenkins.getItemByFullName(job, WorkflowJob.class);
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
            synchronized (LOADING_RUNS) {
                while (r.execution == null && LOADING_RUNS.containsKey(key())) {
                    try {
                        LOADING_RUNS.wait();
                    } catch (InterruptedException x) {
                        LOGGER.log(Level.WARNING, "failed to wait for " + r + " to be loaded", x);
                        break;
                    }
                }
            }
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
        @Override public String getUrl() throws IOException {
            return run().getUrl();
        }
        @Override public String toString() {
            return "Owner[" + key() + ":" + run + "]";
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Owner)) {
                return false;
            }
            Owner that = (Owner) o;
            return job.equals(that.job) && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return job.hashCode() ^ id.hashCode();
        }
        private static final long serialVersionUID = 1;
    }

    private final class GraphL implements GraphListener {
        @Override public void onNewHead(FlowNode node) {
            synchronized (completed) {
                copyLogs();
                logsToCopy.put(node.getId(), 0L);
            }
            node.addAction(new TimingAction());

            logNodeMessage(node, "Running: " + node.getDisplayName());
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

    private void logNodeMessage(FlowNode node, String message) {
        PrintStream logger = listener.getLogger();
        String prefix = getLogPrefix(node);
        if (prefix != null) {
            logger.printf("[%s] %s%n", prefix, message);
        } else {
            logger.println(message);
        }
        // Flushing to keep logs printed in order as much as possible. The copyLogs method uses
        // LargeText and possibly LogLinePrefixOutputFilter. Both of these buffer and flush, causing strange
        // out of sequence writes to the underlying log stream (and => things being printed out of sequence)
        // if we don't flush the logger here.
        logger.flush();
    }

    static void alias() {
        Run.XSTREAM2.alias("flow-build", WorkflowRun.class);
        new XmlFile(null).getXStream().aliasType("flow-owner", Owner.class); // hack! but how else to set it for arbitrary Descriptor’s?
        Run.XSTREAM2.aliasType("flow-owner", Owner.class);
    }

    @Extension public static final class SCMListenerImpl extends SCMListener {
        @Override public void onCheckout(Run<?,?> build, SCM scm, FilePath workspace, TaskListener listener, File changelogFile, SCMRevisionState pollingBaseline) throws Exception {
            if (build instanceof WorkflowRun) {
                ((WorkflowRun) build).onCheckout(scm, workspace, changelogFile, pollingBaseline);
            }
        }
    }

}
