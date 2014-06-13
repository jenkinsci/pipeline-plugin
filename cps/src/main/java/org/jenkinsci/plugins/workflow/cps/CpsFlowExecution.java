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
import com.cloudbees.groovy.cps.CpsTransformer;
import com.cloudbees.groovy.cps.NonCPS;
import com.cloudbees.groovy.cps.Outcome;
import com.cloudbees.groovy.cps.impl.ConstantBlock;
import com.cloudbees.groovy.cps.impl.ThrowBlock;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hudson.model.Action;
import hudson.model.Result;
import jenkins.model.Jenkins;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
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
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.support.concurrent.Futures;
import org.jenkinsci.plugins.workflow.support.pickles.serialization.PickleResolver;
import org.jenkinsci.plugins.workflow.support.pickles.serialization.RiverReader;
import org.jenkinsci.plugins.workflow.support.storage.FlowNodeStorage;
import org.jenkinsci.plugins.workflow.support.storage.SimpleXStreamFlowNodeStorage;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;

/**
 * {@link FlowExecution} implemented with Groovy CPS.
 *
 * <h2>State Transition</h2>
 * <p>
 * {@link CpsFlowExecution} goes through the following states:
 *
 * <pre>
 *                                    +----------------------+
 *                                    |                      |
 *                                    v                      |
 * PERSISTED --> PREPARING --> SUSPENDED --> RUNNABLE --> RUNNING --> COMPLETE
 *                                             ^
 *                                             |
 *                                           INITIAL
 * </pre>
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
    private final String script;
    private /*almost final*/ FlowExecutionOwner owner;

    /**
     * Loading of the program is asynchronous because it requires us to re-obtain stateful objects.
     * This object represents a {@link Future} for filling in {@link CpsThreadGroup}.
     *
     * TODO: provide a mechanism to diagnose how far along this process is.
     *
     * @see #runInCpsVmThread(FutureCallback)
     */
    @Nonnull
    public transient ListenableFuture<CpsThreadGroup> programPromise;

    /**
     * Recreated from {@link #owner}
     */
    /*package*/ transient /*almos final*/ FlowNodeStorage storage;

    /**
     * Start nodes that have been created, whose {@link BlockEndNode} is not yet created.
     */
    /*package*/ final Stack<BlockStartNode> startNodes = new Stack<BlockStartNode>();

    private final NavigableMap<Integer,FlowHead> heads = new TreeMap<Integer, FlowHead>();

    private final AtomicInteger iota = new AtomicInteger();

    private transient List<GraphListener> listeners;

    /**
     * Result set from {@link StepContext}. Start by success and progressively gets worse.
     */
    private Result result = Result.SUCCESS;

    public CpsFlowExecution(String script, FlowExecutionOwner owner) throws IOException {
        this.owner = owner;
        this.script = script;
        this.storage = createStorage();
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
        DSL dsl = new DSL(owner);
        s.getBinding().setVariable("steps", dsl);
        // some of the steps that acquire resources look better with 'with', so exposing
        // that name, such as:
        // with.node('linux') { ... }
        s.getBinding().setVariable("with", dsl);

        s.loadEnvironment();

        final FlowHead h = new FlowHead(this);
        heads.put(h.getId(),h);
        h.newStartNode(new FlowStartNode(this, iotaStr()));

        final CpsThreadGroup g = new CpsThreadGroup(this);
        final SettableFuture<CpsThreadGroup> f = SettableFuture.create();
        g.runner.submit(new Runnable() {
            @Override
            public void run() {
                CpsThread t = g.addThread(new Continuable(s),h,null);
                t.resume(new Outcome(null, null));
                f.set(g);
            }
        });

        programPromise = f;

    }

    private GroovyShell buildShell() {
        ImportCustomizer ic = new ImportCustomizer();
        ic.addStarImports(NonCPS.class.getPackage().getName());
        ic.addStarImports("hudson.model","jenkins.model");
        ic.addStaticStars(CpsBuiltinSteps.class.getName());

        CompilerConfiguration cc = new CompilerConfiguration();
        cc.addCompilationCustomizers(ic);
        cc.addCompilationCustomizers(new CpsTransformer());
        cc.setScriptBaseClass(CpsScript.class.getName());
        Jenkins j = Jenkins.getInstance();
        return new GroovyShell(j!=null ? j.getPluginManager().uberClassLoader : getClass().getClassLoader(), new Binding(), cc);
    }

    private CpsScript parseScript() throws IOException {
        GroovyShell shell = buildShell();
        CpsScript s = (CpsScript) shell.parse(script);
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

    @Override
    public void onLoad() {
        try {
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
            ClassLoader scriptClassLoader = parseScript().getClass().getClassLoader();

            RiverReader r = new RiverReader(programDataFile, scriptClassLoader);
            Futures.addCallback(
                    r.restorePickles(),

                    new FutureCallback<Unmarshaller>() {
                        public void onSuccess(Unmarshaller u) {
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
                        }

                        public void onFailure(Throwable t) {
                            loadProgramFailed(t, result);
                        }
                    });

        } catch (IOException e) {
            loadProgramFailed(e, result);
        }
    }

    /**
     * Used by {@link #loadProgramAsync(File)} to propagate a failure to load the persisted execution state.
     * <p>
     * Let the workflow finish by throwing an exception that indicates how it failed.
     */
    private void loadProgramFailed(Throwable problem, SettableFuture<CpsThreadGroup> promise) {
        FlowHead head;
        switch (heads.size()) {
        case 0:
            // something went catastrophically wrong and there's no live head. fake one
            head = new FlowHead(this);
            try {
                head.newStartNode(new FlowStartNode(this, iotaStr()));
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Failed to persist",e);
            }
            break;
        case 1:
            head = getFirstHead();
            break;
        default:
            // TODO: if there are multiple heads, this will leave all but one dangling. What's the right thing to do?
            head = getFirstHead();
            break;
        }


        CpsThreadGroup g = new CpsThreadGroup(this);

        CpsThread t = g.addThread(
                new Continuable(new ThrowBlock(new ConstantBlock(
                    new IOException("Failed to load persisted workflow state", problem)))),
                head, null
        );
        promise.set(g);
        t.resume(new Outcome(null,null));
    }

    /**
     * Where we store {@link CpsThreadGroup}.
     */
    /*package*/ File getProgramDataFile() throws IOException {
        return new File(owner.getRootDir(), "program.dat");
    }

    /**
     * Execute a task in {@link CpsVmThread} to safely access {@link CpsThreadGroup} internal states.
     *
     * <p>
     * If the {@link CpsThreadGroup} deserializatoin fails, {@link FutureCallback#onFailure(Throwable)} will
     * be invoked (on a random thread that's not {@link CpsVmThread}, since {@link CpsVmThread} cannot exist.)
     */
    void runInCpsVmThread(final FutureCallback<CpsThreadGroup> callback) {
        // first we need to wait for programPromise to fullfil CpsThreadGroup, then we need to run in its runner, phew!
        Futures.addCallback(programPromise, new FutureCallback<CpsThreadGroup>() {
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


    /**
     * Waits for the workflow to move into the SUSPENDED state.
     *
     * @throws Exception
     *      if the workflow has failed
     */
    public void waitForSuspension() throws InterruptedException, ExecutionException {
        CpsThreadGroup g = programPromise.get();
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
    public boolean isCurrentHead(FlowNode n) {
        for (FlowHead h : heads.values()) {
            if (h.get().equals(n))
                return true;
        }
        return false;
    }

    // called by FlowHead to add a new head
    void addHead(FlowHead h) {
        heads.put(h.getId(),h);
    }

    void removeHead(FlowHead h) {
        heads.remove(h.getId());
    }


    @Override
    public void addListener(GraphListener listener) {
        if (listeners == null) {
            listeners = new CopyOnWriteArrayList<GraphListener>();
        }
        listeners.add(listener);
    }

    @Override
    public void finish(Result result) throws IOException, InterruptedException {
        // TODO set FlowEndNode.result to ABORTED
        // TODO: what happens to FlowGraph when we abort it?
        throw new UnsupportedOperationException();
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

    /*packgage*/ synchronized void onProgramEnd(Outcome outcome) {
        // end of the program
        // run till the end successfully FIXME: failure comes here, too
        // TODO: if program terminates with exception, we need to record it
        // TODO: in the error case, we have to close all the open nodes
        FlowNode head = new FlowEndNode(this, iotaStr(), (FlowStartNode)startNodes.pop(), result, getCurrentHeads().toArray(new FlowNode[0]));
        if (outcome.isFailure())
            head.addAction(new ErrorAction(outcome.getAbnormal()));

        // shrink everything into a single new head
        FlowHead first = getFirstHead();
        first.setNewHead(head);
        heads.clear();
        heads.put(first.getId(),first);
    }

    FlowHead getFirstHead() {
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

    // TODO: write a custom XStream Converter so that while we are writing CpsFlowExecution, it holds that lock
    // the execution in Groovy CPS should hold that lock (or worse, hold that lock in the runNextChunk method)
    // so that the execution gets suspended while we are getting serialized

    public static final class ConverterImpl implements Converter {
        private final XStream xs;
        private final ReflectionProvider ref;
        private final Mapper mapper;

        public ConverterImpl(XStream xs) {
            this.xs = xs;
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
            writeChild(w, context, "owner", e.owner, Object.class);
            for (FlowHead h : e.heads.values()) {
                writeChild(w, context, "head", h.getId()+":"+h.get().getId(), String.class);
            }
            writeChild(w, context, "iota", e.iota.get(), Integer.class);

            for (BlockStartNode st : e.startNodes) {
                writeChild(w, context, "start", st.getId(), String.class);
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
            try {
                CpsFlowExecution result;
                if (context.currentObject()!=null) {
                    result = (CpsFlowExecution) context.currentObject();
                } else {
                    result = (CpsFlowExecution) ref.newInstance(CpsFlowExecution.class);
                }

                FlowNodeStorage storage = result.storage;
                Stack<BlockStartNode> startNodes = new Stack<BlockStartNode>();
                Map<Integer,FlowHead> heads = new TreeMap<Integer, FlowHead>();

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
                    if (nodeName.equals("owner")) {
                        FlowExecutionOwner owner = (FlowExecutionOwner) readChild(reader, context, Object.class, result);
                        setField(result, "owner", owner);
                        setField(result, "storage", storage = result.createStorage());
                    } else
                    if (nodeName.equals("head")) {
                        String[] head = readChild(reader, context, String.class, result).split(":");
                        FlowHead h = new FlowHead(result, Integer.parseInt(head[0]));
                        h.setForDeserialize(storage.getNode(head[1]));
                        heads.put(h.getId(), h);
                    } else
                    if (nodeName.equals("iota")) {
                        Integer iota = readChild(reader, context, Integer.class, result);
                        setField(result, "iota", new AtomicInteger(iota));
                    } else
                    if (nodeName.equals("start")) {
                        String id = readChild(reader, context, String.class, result);
                        startNodes.add((BlockStartNode) storage.getNode(id));
                    }

                    reader.moveUp();
                }

                setField(result, "startNodes", startNodes);
                setField(result, "heads", heads);

                return result;
            } catch (IOException e) {
                throw new XStreamException("Failed to read back CpsFlowExecution",e);
            }
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
