package org.jenkinsci.plugins.workflow.flow;

import com.google.common.collect.AbstractIterator;
import com.google.inject.Inject;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.listeners.ItemListener;
import hudson.remoting.SingleLaneExecutorService;
import hudson.util.CopyOnWriteList;
import jenkins.model.Jenkins;
import jenkins.util.Timer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * Tracks the running {@link FlowExecution}s so that it can be enumerated.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class FlowExecutionList implements Iterable<FlowExecution> {
    private CopyOnWriteList<FlowExecutionOwner> runningTasks = new CopyOnWriteList<FlowExecutionOwner>();
    private final SingleLaneExecutorService executor = new SingleLaneExecutorService(Timer.get());

    private XmlFile getConfigFile() {
        return new XmlFile(new File(Jenkins.getInstance().getRootDir(), FlowExecutionList.class.getName() + ".xml"));
    }

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
                        // TODO: we need some means to remove stale entries from the list, but
                        // this might be too drastic a measure (and it can be invoked too early,
                        // for example before Jenkins fully loads all the jobs)
                        LOGGER.log(WARNING, "Failed to load " + o + ". Unregistering", e);
                        unregister(o);
                    }
                }
                return endOfData();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private synchronized void load() {
        XmlFile configFile = getConfigFile();
        if (configFile.exists()) {
            try {
                runningTasks.replaceBy((List<FlowExecutionOwner>) configFile.read());
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
        saveLater();
    }

    private synchronized void saveLater() {
        executor.submit(new Runnable() {
            final List<FlowExecutionOwner> copy = new ArrayList<FlowExecutionOwner>(runningTasks.getView());

            @Override
            public void run() {
                try {
                    getConfigFile().write(copy);
                } catch (IOException x) {
                    LOGGER.log(WARNING, null, x);
                }
            }
        });
    }

    private static final Logger LOGGER = Logger.getLogger(FlowExecutionList.class.getName());

    public static FlowExecutionList get() {
        return Jenkins.getInstance().getInjector().getInstance(FlowExecutionList.class);
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
                LOGGER.fine("Eager loading "+e);
            }
        }
    }
}
