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

package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Envs;
import com.cloudbees.groovy.cps.Outcome;
import com.cloudbees.groovy.cps.impl.ConstantBlock;
import com.cloudbees.groovy.cps.impl.ThrowBlock;
import com.cloudbees.groovy.cps.sandbox.DefaultInvoker;
import com.cloudbees.groovy.cps.sandbox.SandboxInvoker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import groovy.lang.GroovyShell;
import hudson.model.Action;
import hudson.model.Result;
import hudson.util.Iterators;
import jenkins.model.CauseOfInterruption;
import jenkins.model.Jenkins;
import org.jboss.marshalling.Unmarshaller;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.support.concurrent.Futures;
import org.jenkinsci.plugins.workflow.support.pickles.serialization.PickleResolver;
import org.jenkinsci.plugins.workflow.support.pickles.serialization.RiverReader;
import org.jenkinsci.plugins.workflow.support.storage.FlowNodeStorage;
import org.jenkinsci.plugins.workflow.support.storage.SimpleXStreamFlowNodeStorage;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Stack;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper.*;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.init.Terminator;
import hudson.model.User;
import hudson.security.ACL;
import java.beans.Introspector;
import java.util.LinkedHashMap;
import javax.annotation.CheckForNull;
import javax.annotation.concurrent.GuardedBy;

import org.acegisecurity.Authentication;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.jboss.marshalling.reflect.SerializableClassRegistry;
import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionList;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * {@link FlowExecution} implemented with Groovy CPS.
 *
 * <h2>State Transition</h2>
 * <p>
 * {@link CpsFlowExecution} goes through the following states:
 *
 * <pre>{@code
 *                                    +----------------------+
 *                                    |                      |
 *                                    v                      |
 * PERSISTED --> PREPARING --> SUSPENDED --> RUNNABLE --> RUNNING --> COMPLETE
 *                                             ^
 *                                             |
 *                                           INITIAL
 * }</pre>
 *
 * <dl>
 * <dt>INITIAL</dt>
 * <dd>
 *     When a new {@link CpsFlowExecution} is created, it starts from here.
 *
 *     When {@link #start()} method is called, we get one thread scheduled, and we arrive at RUNNABLE state.
 * </dd>
 * <dt>PERSISTED</dt>
 * <dd>
 *     {@link CpsFlowExecution} is on disk with its owner, for example in <tt>build.xml</tt> of the workflow run.
 *     Nothing exists in memory. For example, Jenkins is not running.
 *
 *     Transition from this into PREPARING is triggered outside our control by XStream using
 *     {@link ConverterImpl} to unmarshal {@link CpsFlowExecution}. {@link #onLoad()} is called
 *     at the end, and we arrive at the PREPARING state.
 * </dd>
 * <dt>PREPARING</dt>
 * <dd>
 *     {@link CpsFlowExecution} is in memory, but {@link CpsThreadGroup} isn't. We are trying to
 *     restore all the ephemeral pickles that are necessary to get workflow going again.
 *     {@link #programPromise} represents a promise of completing this state.
 *
 *     {@link PickleResolver} keeps track of this, and when it's all done, we arrive at SUSPENDED state.
 * </dd>
 * <dt>SUSPENDED</dt>
 * <dd>
 *     {@link CpsThreadGroup} is in memory, but all {@link CpsThread}s are {@linkplain CpsThread#isRunnable() not runnable},
 *     which means they are waiting for some conditions to trigger (such as a completion of a shell script that's executing,
 *     human approval, etc). {@link CpsFlowExecution} and {@link CpsThreadGroup} are safe to persist.
 *
 *     When a condition is met, {@link CpsThread#resume(Outcome)} is called, and that thread becomes runnable,
 *     and we move to the RUNNABLE state.
 * </dd>
 * <dt>RUNNABLE</dt>
 * <dd>
 *     Some of {@link CpsThread}s are runnable, but we aren't actually running. The conditions that triggered
 *     {@link CpsThread} is captured in {@link CpsThread#resumeValue}.
 *     As we get into this state, {@link CpsThreadGroup#scheduleRun()}
 *     should be called to schedule the execution.
 *     {@link CpsFlowExecution} and {@link CpsThreadGroup} are safe to persist in this state, just like in the SUSPENDED state.
 *
 *     When {@link CpsThreadGroup#runner} allocated a real Java thread to the execution, we move to the RUNNING state.
 * </dd>
 * <dt>RUNNING</dt>
 * <dd>
 *     A thread is inside {@link CpsThreadGroup#run()} and is actively mutating the object graph inside the script.
 *     This state continues until no threads are runnable any more.
 *     Only one thread executes {@link CpsThreadGroup#run()}.
 *
 *     In this state, {@link CpsFlowExecution} still need to be persistable (because generally we don't get to
 *     control when it is persisted), but {@link CpsThreadGroup} isn't safe to persist.
 *
 *     When the Java thread leaves {@link CpsThreadGroup#run()}, we move to the SUSPENDED state.
 * </dd>
 * <dt>COMPLETE</dt>
 * <dd>
 *     All the {@link CpsThread}s have terminated and there's nothing more to execute, and there's no more events to wait.
 *     The result is finalized and there's no further state change.
 * </dd>
 * </dl>
 *
 * @author Kohsuke Kawaguchi
 */
@PersistIn(RUN)
public class CpsFlowExecution extends FlowExecution {
    /**
     * Groovy script of the main source file (that the user enters in the GUI)
     */
    private final String script;

    /**
     * Any additional scripts {@linkplain CpsGroovyShell#parse(String) parsed} afterward, keyed by
     * their FQCN.
     */
    /*package*/ /*final*/ Map<String,String> loadedScripts = new HashMap<String, String>();

    private final boolean sandbox;
    private transient /*almost final*/ FlowExecutionOwner owner;

    /**
     * Loading of the program is asynchronous because it requires us to re-obtain stateful objects.
     * This object represents a {@link Future} for filling in {@link CpsThreadGroup}.
     *
     * TODO: provide a mechanism to diagnose how far along this process is.
     *
     * @see #runInCpsVmThread(FutureCallback)
     */
    public transient volatile ListenableFuture<CpsThreadGroup> programPromise;

    /**
     * Recreated from {@link #owner}
     */
    /*package*/ transient /*almos final*/ FlowNodeStorage storage;

    /** User ID associated with this build, or null if none specific. */
    private final @CheckForNull String user;

    /**
     * Start nodes that have been created, whose {@link BlockEndNode} is not yet created.
     */
    @GuardedBy("this")
    /*package*/ /* almost final*/ Stack<BlockStartNode> startNodes = new Stack<BlockStartNode>();
    @SuppressFBWarnings({"IS_FIELD_NOT_GUARDED", "IS2_INCONSISTENT_SYNC"}) // irrelevant here
    private transient List<String> startNodesSerial; // used only between unmarshal and onLoad

    @GuardedBy("this")
    private /* almost final*/ NavigableMap<Integer,FlowHead> heads = new TreeMap<Integer,FlowHead>();
    @SuppressFBWarnings({"IS_FIELD_NOT_GUARDED", "IS2_INCONSISTENT_SYNC"}) // irrelevant here
    private transient Map<Integer,String> headsSerial; // used only between unmarshal and onLoad

    private final AtomicInteger iota = new AtomicInteger();

    private transient List<GraphListener> listeners;

    /**
     * Result set from {@link StepContext}. Start by success and progressively gets worse.
     */
    private Result result = Result.SUCCESS;

    /**
     * When the program is completed, set to true.
     *
     * {@link FlowExecution} gets loaded into memory for the build records that have been completed,
     * and for those we don't want to load the program state, so that check should be efficient.
     */
    private boolean done;

    /**
     * Groovy compiler with CPS+sandbox transformation correctly setup.
     * By the time the script starts running, this field is set to non-null.
     * It is reset to null after completion.
     */
    private transient CpsGroovyShell shell;
    /** Class of the {@link CpsScript}; its loader is a {@link groovy.lang.GroovyClassLoader.InnerLoader}, not the same as {@code shell.getClassLoader()}. */
    private transient Class<?> scriptClass;

    /** Actions to add to the {@link FlowStartNode}. */
    transient final List<Action> flowStartNodeActions = new ArrayList<Action>();

    @Deprecated
    public CpsFlowExecution(String script, FlowExecutionOwner owner) throws IOException {
        this(script, false, owner);
    }

    protected CpsFlowExecution(String script, boolean sandbox, FlowExecutionOwner owner) throws IOException {
        this.owner = owner;
        this.script = script;
        this.sandbox = sandbox;
        this.storage = createStorage();
        Authentication auth = Jenkins.getAuthentication();
        this.user = auth.equals(ACL.SYSTEM) ? null : auth.getName();
    }

    /**
     * Perform post-deserialization state resurrection that handles version evolution
     */
    private Object readResolve() {
        if (loadedScripts==null)
            loadedScripts = new HashMap<String,String>();   // field added later
        return this;
    }

    /**
     * Returns a groovy compiler used to load the script.
     *
     * @see GroovyShell#getClassLoader()
     */
    public GroovyShell getShell() {
        return shell;
    }

    public FlowNodeStorage getStorage() {
        return storage;
    }

    /**
     * True if executing with groovy-sandbox, false if executing with approval.
     */
    public boolean isSandbox() {
        return sandbox;
    }

    @Override
    public FlowExecutionOwner getOwner() {
        return owner;
    }

    private SimpleXStreamFlowNodeStorage createStorage() throws IOException {
        return new SimpleXStreamFlowNodeStorage(this, getStorageDir());
    }

    /**
     * Directory where workflow stores its state.
     */
    public File getStorageDir() throws IOException {
        return new File(this.owner.getRootDir(),"workflow");
    }

    @Override
    public void start() throws IOException {
        final CpsScript s = parseScript();
        scriptClass = s.getClass();
        s.$initialize();

        final FlowHead h = new FlowHead(this);
        synchronized (this) {
            heads.put(h.getId(), h);
        }
        h.newStartNode(new FlowStartNode(this, iotaStr()));

        final CpsThreadGroup g = new CpsThreadGroup(this);
        final SettableFuture<CpsThreadGroup> f = SettableFuture.create();
        programPromise = f;
        g.runner.submit(new Runnable() {
            @Override
            public void run() {
                CpsThread t = g.addThread(new Continuable(s,createInitialEnv()),h,null);
                t.resume(new Outcome(null, null));
                f.set(g);
            }

            /**
             * Environment to start executing the script in.
             * During sandbox execution, we need to call sandbox interceptor while executing asynchronous code.
             */
            private Env createInitialEnv() {
                return Envs.empty( isSandbox() ? new SandboxInvoker() : new DefaultInvoker());
            }
        });
    }

    private CpsScript parseScript() throws IOException {
        shell = new CpsGroovyShell(this);
        CpsScript s = (CpsScript) shell.reparse("WorkflowScript",script);

        for (Entry<String, String> e : loadedScripts.entrySet()) {
            shell.reparse(e.getKey(), e.getValue());
        }

        s.execution = this;
        if (false) {
            System.out.println("scriptName="+s.getClass().getName());
            System.out.println(Arrays.asList(s.getClass().getInterfaces()));
            System.out.println(Arrays.asList(s.getClass().getDeclaredFields()));
            System.out.println(Arrays.asList(s.getClass().getDeclaredMethods()));
        }
        return s;
    }

    /**
     * Assigns a new ID.
     */
    @Restricted(NoExternalUse.class)
    public String iotaStr() {
        return String.valueOf(iota());
    }

    @Restricted(NoExternalUse.class)
    public int iota() {
        return iota.incrementAndGet();
    }

    protected void initializeStorage() throws IOException {
        storage = createStorage();
        synchronized (this) {
            // heads could not be restored in unmarshal, so doing that now:
            heads = new TreeMap<Integer,FlowHead>();
            for (Map.Entry<Integer,String> entry : headsSerial.entrySet()) {
                FlowHead h = new FlowHead(this, entry.getKey());
                h.setForDeserialize(storage.getNode(entry.getValue()));
                heads.put(h.getId(), h);
            }
            headsSerial = null;
            // Same for startNodes:
            startNodes = new Stack<BlockStartNode>();
            for (String id : startNodesSerial) {
                startNodes.add((BlockStartNode) storage.getNode(id));
            }
            startNodesSerial = null;
        }
    }

    @Override
    public void onLoad(FlowExecutionOwner owner) throws IOException {
        this.owner = owner;
        initializeStorage();
        try {
            if (!isComplete())
                loadProgramAsync(getProgramDataFile());
        } catch (IOException e) {
            SettableFuture<CpsThreadGroup> p = SettableFuture.create();
            programPromise = p;
            loadProgramFailed(e, p);
        }
    }

    /**
     * Deserializes {@link CpsThreadGroup} from {@link #getProgramDataFile()} if necessary.
     *
     * This moves us into the PREPARING state.
     * @param programDataFile
     */
    public void loadProgramAsync(File programDataFile) {
        final SettableFuture<CpsThreadGroup> result = SettableFuture.create();
        programPromise = result;

        try {
            scriptClass = parseScript().getClass();

            final RiverReader r = new RiverReader(programDataFile, scriptClass.getClassLoader(), owner);
            Futures.addCallback(
                    r.restorePickles(),

                    new FutureCallback<Unmarshaller>() {
                        public void onSuccess(Unmarshaller u) {
                            try {
                            CpsFlowExecution old = PROGRAM_STATE_SERIALIZATION.get();
                            PROGRAM_STATE_SERIALIZATION.set(CpsFlowExecution.this);
                            try {
                                CpsThreadGroup g = (CpsThreadGroup) u.readObject();
                                result.set(g);
                            } catch (Throwable t) {
                                onFailure(t);
                            } finally {
                                PROGRAM_STATE_SERIALIZATION.set(old);
                            }
                            } finally {
                                r.close();
                            }
                        }

                        public void onFailure(Throwable t) {
                            // Note: not calling result.setException(t) since loadProgramFailed in fact sets a result
                            try {
                                loadProgramFailed(t, result);
                            } finally {
                                r.close();
                            }
                        }
                    });

        } catch (IOException e) {
            loadProgramFailed(e, result);
        }
    }

    /**
     * Used by {@link #loadProgramAsync(File)} to propagate a failure to load the persisted execution state.
     * <p>
     * Let the workflow interrupt by throwing an exception that indicates how it failed.
     * @param promise same as {@link #programPromise} but more strongly typed
     */
    private void loadProgramFailed(final Throwable problem, SettableFuture<CpsThreadGroup> promise) {
        FlowHead head;

        synchronized(this) {
            if (heads.isEmpty())
                head = null;
            else
                head = getFirstHead();
        }

        if (head==null) {
            // something went catastrophically wrong and there's no live head. fake one
            head = new FlowHead(this);
            try {
                head.newStartNode(new FlowStartNode(this, iotaStr()));
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Failed to persist", e);
            }
        }


        CpsThreadGroup g = new CpsThreadGroup(this);
        final FlowHead head_ = head;

        promise.set(g);
        runInCpsVmThread(new FutureCallback<CpsThreadGroup>() {
            @Override public void onSuccess(CpsThreadGroup g) {
                CpsThread t = g.addThread(
                        new Continuable(new ThrowBlock(new ConstantBlock(
                            new IOException("Failed to load persisted workflow state", problem)))),
                        head_, null
                );
                t.resume(new Outcome(null,null));
            }
            @Override public void onFailure(Throwable t) {
                LOGGER.log(Level.WARNING, null, t);
            }
        });
    }

    /**
     * Where we store {@link CpsThreadGroup}.
     */
    /*package*/ File getProgramDataFile() throws IOException {
        return new File(owner.getRootDir(), "program.dat");
    }

    /**
     * Execute a task in {@link CpsVmExecutorService} to safely access {@link CpsThreadGroup} internal states.
     *
     * <p>
     * If the {@link CpsThreadGroup} deserializatoin fails, {@link FutureCallback#onFailure(Throwable)} will
     * be invoked (on a random thread, since {@link CpsVmThread} doesn't exist without a valid program.)
     */
    void runInCpsVmThread(final FutureCallback<CpsThreadGroup> callback) {
        if (programPromise == null) {
            throw new IllegalStateException("broken flow");
        }
        // first we need to wait for programPromise to fullfil CpsThreadGroup, then we need to run in its runner, phew!
        Futures.addCallback(programPromise, new FutureCallback<CpsThreadGroup>() {
            final Exception source = new Exception();   // call stack of this object captures who called this. useful during debugging.
            @Override
            public void onSuccess(final CpsThreadGroup g) {
                g.runner.submit(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(g);
                    }
                });
            }

            /**
             * Program state failed to load.
             */
            @Override
            public void onFailure(Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    @Override public boolean blocksRestart() {
        if (programPromise == null || !programPromise.isDone()) {
            return true;
        }
        CpsThreadGroup g;
        try {
            g = programPromise.get();
        } catch (Exception x) {
            return true;
        }
        return g.busy;
    }

    /**
     * Waits for the workflow to move into the SUSPENDED state.
     */
    public void waitForSuspension() throws InterruptedException, ExecutionException {
        if (programPromise==null)
            return; // the execution has already finished and we are not loading program state anymore
        CpsThreadGroup g = programPromise.get();
        // TODO occasionally tests fail here with RejectedExecutionException, apparently because the runner has been shut down; should we just ignore that?
        g.scheduleRun().get();
    }

    public synchronized FlowHead getFlowHead(int id) {
        return heads.get(id);
    }

    @Override
    public synchronized List<FlowNode> getCurrentHeads() {
        List<FlowNode> r = new ArrayList<FlowNode>();
        for (FlowHead h : heads.values()) {
            r.add(h.get());
        }
        return r;
    }

    @Override
    public ListenableFuture<List<StepExecution>> getCurrentExecutions(final boolean innerMostOnly) {
        if (programPromise == null || isComplete()) {
            return Futures.immediateFuture(Collections.<StepExecution>emptyList());
        }

        final SettableFuture<List<StepExecution>> r = SettableFuture.create();
        runInCpsVmThread(new FutureCallback<CpsThreadGroup>() {
            @Override
            public void onSuccess(CpsThreadGroup g) {
                if (innerMostOnly) {
                    // to exclude outer StepExecutions, first build a map by FlowHead
                    // younger threads with their StepExecutions will overshadow old threads, leaving inner-most threads alone.
                    Map<FlowHead, StepExecution> m = new LinkedHashMap<FlowHead, StepExecution>();
                    for (CpsThread t : g.threads.values()) {
                        StepExecution e = t.getStep();
                        if (e != null) {
                            m.put(t.head, e);
                        }
                    }
                    r.set(ImmutableList.copyOf(m.values()));
                } else {
                    List<StepExecution> es = new ArrayList<StepExecution>();
                    for (CpsThread t : g.threads.values()) {
                        StepExecution e = t.getStep();
                        if (e != null) {
                            es.add(e);
                        }
                    }
                    r.set(Collections.unmodifiableList(es));
                }
            }

            @Override
            public void onFailure(Throwable t) {
                r.setException(t);
            }
        });

        return r;
    }

    @Override
    public synchronized boolean isCurrentHead(FlowNode n) {
        for (FlowHead h : heads.values()) {
            if (h.get().equals(n))
                return true;
        }
        return false;
    }

    /**
     * Called by FlowHead to add a new head.
     *
     * The new head gets removed via {@link #subsumeHead(FlowNode)} when it's used as a parent
     * of a FlowNode and thereby joining an another thread.
     */
    //
    synchronized void addHead(FlowHead h) {
        heads.put(h.getId(), h);
    }

    synchronized void removeHead(FlowHead h) {
        heads.remove(h.getId());
    }

    /**
     * Removes a {@link FlowHead} that points to the given node from the 'current heads' list.
     *
     * This is used when a thread waits and collects the outcome of another thread.
     */
    void subsumeHead(FlowNode n) {
        List<FlowHead> _heads;
        synchronized (this) {
            _heads = new ArrayList<FlowHead>(heads.values());
        }
        for (FlowHead h : _heads) {
            if (h.get()==n) {
                h.remove();
                return;
            }
        }
    }


    @Override
    public void addListener(GraphListener listener) {
        if (listeners == null) {
            listeners = new CopyOnWriteArrayList<GraphListener>();
        }
        listeners.add(listener);
    }

    @Override
    public void interrupt(Result result, CauseOfInterruption... causes) throws IOException, InterruptedException {
        setResult(result);

        final FlowInterruptedException ex = new FlowInterruptedException(result,causes);

        // stop all ongoing activities
        Futures.addCallback(getCurrentExecutions(/* cf. JENKINS-26148 */true), new FutureCallback<List<StepExecution>>() {
            @Override
            public void onSuccess(List<StepExecution> l) {
                for (StepExecution e : Iterators.reverse(l)) {
                    try {
                        e.stop(ex);
                    } catch (Exception x) {
                        LOGGER.log(Level.WARNING, "Failed to abort " + CpsFlowExecution.this.toString(), x);
                    }
                }
            }

            @Override
            public void onFailure(Throwable t) {
            }
        });
    }

    @Override
    public FlowNode getNode(String id) throws IOException {
        return storage.getNode(id);
    }

    public void setResult(Result v) {
        result = result.combine(v);
    }

    public Result getResult() {
        return result;
    }

    public List<Action> loadActions(FlowNode node) throws IOException {
        return storage.loadActions(node);
    }

    public void saveActions(FlowNode node, List<Action> actions) throws IOException {
        storage.saveActions(node, actions);
    }

    @Override
    public boolean isComplete() {
        return done || super.isComplete();
    }

    /*packgage*/ synchronized void onProgramEnd(Outcome outcome) {
        // end of the program
        // run till the end successfully FIXME: failure comes here, too
        // TODO: if program terminates with exception, we need to record it
        // TODO: in the error case, we have to close all the open nodes
        FlowNode head = new FlowEndNode(this, iotaStr(), (FlowStartNode)startNodes.pop(), result, getCurrentHeads().toArray(new FlowNode[0]));
        if (outcome.isFailure())
            head.addAction(new ErrorAction(outcome.getAbnormal()));

        // shrink everything into a single new head
        done = true;
        FlowHead first = getFirstHead();
        first.setNewHead(head);
        heads.clear();
        heads.put(first.getId(),first);

        // clean up heap
        shell = null;
        SerializableClassRegistry.getInstance().release(scriptClass.getClassLoader());
        Introspector.flushFromCaches(scriptClass); // does not handle other derived script classes, but this is only SoftReference anyway
        scriptClass = null;
    }

    synchronized FlowHead getFirstHead() {
        assert !heads.isEmpty();
        return heads.firstEntry().getValue();
    }

    void notifyListeners(FlowNode node) {
        if (listeners != null) {
            for (GraphListener listener : listeners) {
                listener.onNewHead(node);
            }
        }
    }

    @Override public Authentication getAuthentication() {
        if (user == null) {
            return ACL.SYSTEM;
        }
        try {
            return User.get(user).impersonate();
        } catch (UsernameNotFoundException x) {
            LOGGER.log(Level.WARNING, "could not restore authentication", x);
            // Should not expose this to callers.
            return Jenkins.ANONYMOUS;
        }
    }

    @Override public String toString() {
        return "CpsFlowExecution[" + owner + "]";
    }

    @Restricted(DoNotUse.class)
    @Terminator public static void suspendAll() throws InterruptedException, ExecutionException {
        LOGGER.fine("starting to suspend all executions");
        for (FlowExecution execution : FlowExecutionList.get()) {
            if (execution instanceof CpsFlowExecution) {
                LOGGER.log(Level.FINE, "waiting to suspend {0}", execution);
                ((CpsFlowExecution) execution).waitForSuspension();
            }
        }
        LOGGER.fine("finished suspending all executions");
    }

    // TODO: write a custom XStream Converter so that while we are writing CpsFlowExecution, it holds that lock
    // the execution in Groovy CPS should hold that lock (or worse, hold that lock in the runNextChunk method)
    // so that the execution gets suspended while we are getting serialized

    public static final class ConverterImpl implements Converter {
        private final ReflectionProvider ref;
        private final Mapper mapper;

        public ConverterImpl(XStream xs) {
            this.ref = xs.getReflectionProvider();
            this.mapper = xs.getMapper();
        }

        public boolean canConvert(Class type) {
            return CpsFlowExecution.class==type;
        }

        public void marshal(Object source, HierarchicalStreamWriter w, MarshallingContext context) {
            CpsFlowExecution e = (CpsFlowExecution) source;

            writeChild(w, context, "result", e.result, Result.class);
            writeChild(w, context, "script", e.script, String.class);
            writeChild(w, context, "loadedScripts", e.loadedScripts, Map.class);
            if (e.user != null) {
                writeChild(w, context, "user", e.user, String.class);
            }
            writeChild(w, context, "iota", e.iota.get(), Integer.class);
            synchronized (e) {
                for (FlowHead h : e.heads.values()) {
                    writeChild(w, context, "head", h.getId() + ":" + h.get().getId(), String.class);
                }
                for (BlockStartNode st : e.startNodes) {
                    writeChild(w, context, "start", st.getId(), String.class);
                }
            }
        }

        private <T> void writeChild(HierarchicalStreamWriter w, MarshallingContext context, String name, T v, Class<T> staticType) {
            if (!mapper.shouldSerializeMember(CpsFlowExecution.class,name))
                return;
            startNode(w, name, staticType);
            Class<?> actualType = v.getClass();
            if (actualType !=staticType)
                w.addAttribute(mapper.aliasForSystemAttribute("class"), mapper.serializedClass(actualType));

            context.convertAnother(v);
            w.endNode();
        }

        public Object unmarshal(HierarchicalStreamReader reader, final UnmarshallingContext context) {
                CpsFlowExecution result;
                if (context.currentObject()!=null) {
                    result = (CpsFlowExecution) context.currentObject();
                } else {
                    result = (CpsFlowExecution) ref.newInstance(CpsFlowExecution.class);
                }

                result.startNodesSerial = new ArrayList<String>();
                result.headsSerial = new TreeMap<Integer,String>();

                while (reader.hasMoreChildren()) {
                    reader.moveDown();

                    String nodeName = reader.getNodeName();
                    if (nodeName.equals("result")) {
                        Result r = readChild(reader, context, Result.class, result);
                        setField(result, "result", r);
                    } else
                    if (nodeName.equals("script")) {
                        String script = readChild(reader, context, String.class, result);
                        setField(result, "script", script);
                    } else
                    if (nodeName.equals("loadedScripts")) {
                        Map loadedScripts = readChild(reader, context, Map.class, result);
                        setField(result, "loadedScripts", loadedScripts);
                    } else
                    if (nodeName.equals("owner")) {
                        readChild(reader, context, Object.class, result); // for compatibility; discarded
                    } else
                    if (nodeName.equals("user")) {
                        String user = readChild(reader, context, String.class, result);
                        setField(result, "user", user);
                    } else
                    if (nodeName.equals("head")) {
                        String[] head = readChild(reader, context, String.class, result).split(":");
                        result.headsSerial.put(Integer.parseInt(head[0]), head[1]);
                    } else
                    if (nodeName.equals("iota")) {
                        Integer iota = readChild(reader, context, Integer.class, result);
                        setField(result, "iota", new AtomicInteger(iota));
                    } else
                    if (nodeName.equals("start")) {
                        String id = readChild(reader, context, String.class, result);
                        result.startNodesSerial.add(id);
                    }

                    reader.moveUp();
                }

                return result;
        }

        private void setField(CpsFlowExecution result, String fieldName, Object value) {
            ref.writeField(result, fieldName, value, CpsFlowExecution.class);
        }

        /**
         * Called when a reader is
         */
        private <T> T readChild(HierarchicalStreamReader r, UnmarshallingContext context, Class<T> type, Object parent) {
            String classAttribute = r.getAttribute(mapper.aliasForAttribute("class"));
            if (classAttribute != null) {
                type = mapper.realClass(classAttribute);
            }

            return type.cast(context.convertAnother(parent, type));
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CpsFlowExecution.class.getName());

    /**
     * While we serialize/deserialize {@link CpsThreadGroup} and the entire program execution state,
     * this field is set to {@link CpsFlowExecution} that will own it.
     */
    static final ThreadLocal<CpsFlowExecution> PROGRAM_STATE_SERIALIZATION = new ThreadLocal<CpsFlowExecution>();
}
