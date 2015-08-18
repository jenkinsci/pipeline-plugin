package org.jenkinsci.plugins.workflow.flow;

import com.google.common.base.Function;
import com.google.common.collect.AbstractIterator;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import hudson.Extension;
import hudson.XmlFile;
import hudson.init.Terminator;
import hudson.model.listeners.ItemListener;
import hudson.remoting.SingleLaneExecutorService;
import hudson.util.CopyOnWriteList;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecutionIterator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.*;
import javax.annotation.CheckForNull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * Tracks the running {@link FlowExecution}s so that it can be enumerated.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class FlowExecutionList implements Iterable<FlowExecution> {
    private CopyOnWriteList<FlowExecutionOwner> runningTasks = new CopyOnWriteList<FlowExecutionOwner>();
    private final SingleLaneExecutorService executor = new SingleLaneExecutorService(Timer.get());
    private XmlFile configFile;

    public FlowExecutionList() {
        load();
    }

    /**
     * Lists all the current {@link FlowExecutionOwner}s.
     */
    @Override
    public Iterator<FlowExecution> iterator() {
        return new AbstractIterator<FlowExecution>() {
            final Iterator<FlowExecutionOwner> base = runningTasks.iterator();

            @Override
            protected FlowExecution computeNext() {
                while (base.hasNext()) {
                    FlowExecutionOwner o = base.next();
                    try {
                        FlowExecution e = o.get();
                        if (e.isComplete()) {
                            unregister(o);
                        } else {
                            return e;
                        }
                    } catch (IOException e) {
                        LOGGER.log(WARNING, "Failed to load " + o + ". Unregistering", e);
                        unregister(o);
                    }
                }
                return endOfData();
            }
        };
    }

    private synchronized @CheckForNull XmlFile configFile() {
        if (configFile == null) {
            Jenkins j = Jenkins.getInstance();
            if (j != null) {
                configFile = new XmlFile(new File(j.getRootDir(), FlowExecutionList.class.getName() + ".xml"));
            }
        }
        return configFile;
    }

    @SuppressWarnings("unchecked")
    private synchronized void load() {
        XmlFile cf = configFile();
        if (cf == null) {
            return; // oh well
        }
        if (cf.exists()) {
            try {
                runningTasks.replaceBy((List<FlowExecutionOwner>) cf.read());
            } catch (IOException x) {
                LOGGER.log(WARNING, null, x);
            }
        }
    }

    /**
     * It is the responsibility of the {@link FlowExecutionOwner} to register itself before it starts executing.
     * And likewise, unregister itself after it is completed, even though this class does clean up entries that
     * are no longer running.
     */
    public synchronized void register(final FlowExecutionOwner self) {
        load();
        if (!runningTasks.contains(self))
            runningTasks.add(self);
        saveLater();
    }

    public synchronized void unregister(final FlowExecutionOwner self) {
        load();
        runningTasks.remove(self);
        LOGGER.log(FINE, "unregistered {0} so is now {1}", new Object[] {self, runningTasks.getView()});
        saveLater();
    }

    private synchronized void saveLater() {
        final List<FlowExecutionOwner> copy = new ArrayList<FlowExecutionOwner>(runningTasks.getView());
        try {
            executor.submit(new Runnable() {
                @Override public void run() {
                    save(copy);
                }
            });
        } catch (RejectedExecutionException x) {
            LOGGER.log(FINE, "could not schedule save, perhaps because Jenkins is shutting down; saving immediately", x);
            save(copy);
        }
    }
    private void save(List<FlowExecutionOwner> copy) {
        XmlFile cf = configFile();
        LOGGER.log(FINE, "saving {0} to {1}", new Object[] {copy, cf});
        if (cf == null) {
            return; // oh well
        }
        try {
            cf.write(copy);
        } catch (IOException x) {
            LOGGER.log(WARNING, null, x);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(FlowExecutionList.class.getName());

    public static FlowExecutionList get() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) { // might be called during shutdown
            return new FlowExecutionList();
        }
        return j.getInjector().getInstance(FlowExecutionList.class);
    }

    /**
     * When Jenkins starts up and everything is loaded, be sure to proactively resurrect
     * all the ongoing {@link FlowExecution}s so that they start running again.
     */
    @Extension
    public static class ItemListenerImpl extends ItemListener {
        @Inject
        FlowExecutionList list;

        @Override
        public void onLoaded() {
            for (FlowExecution e : list) {
                LOGGER.log(FINE, "Eager loading {0}", e);
                Futures.addCallback(e.getCurrentExecutions(false), new FutureCallback<List<StepExecution>>() {
                    @Override
                    public void onSuccess(List<StepExecution> result) {
                        for (StepExecution se : result) {
                            se.onResume();
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {

                    }
                });
            }
        }
    }

    /**
     * Enumerates {@link StepExecution}s running inside {@link FlowExecution}.
     */
    @Extension
    public static class StepExecutionIteratorImpl extends StepExecutionIterator {
        @Inject
        FlowExecutionList list;

        @Override
        public ListenableFuture<?> apply(final Function<StepExecution, Void> f) {
            List<ListenableFuture<?>> all = new ArrayList<ListenableFuture<?>>();

            for (FlowExecution e : list) {
                ListenableFuture<List<StepExecution>> execs = e.getCurrentExecutions(false);
                all.add(execs);
                Futures.addCallback(execs,new FutureCallback<List<StepExecution>>() {
                    @Override
                    public void onSuccess(List<StepExecution> result) {
                        for (StepExecution e : result) {
                            try {
                                f.apply(e);
                            } catch (RuntimeException x) {
                                LOGGER.log(Level.WARNING, null, x);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LOGGER.log(Level.WARNING, null, t);
                    }
                });
            }

            return Futures.allAsList(all);
        }
    }

    @Restricted(DoNotUse.class)
    @Terminator public static void saveAll() throws InterruptedException {
        LOGGER.fine("ensuring all executions are saved");
        SingleLaneExecutorService executor = get().executor;
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }

}
