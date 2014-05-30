package org.jenkinsci.plugins.workflow.cps.persistence;

import hudson.model.Job;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/**
 * @author Kohsuke Kawaguchi
 */
public enum PersistenceContext {
    /**
     * A class gets persisted in the {@code build.xml} as a part of
     * {@link Run} through XStream.
     */
    RUN,
    /**
     * A class gets persisted in the {@code config.xml} as a part of
     * {@link Job} through XStream.
     */
    JOB,
    /**
     * A class is a "handle" that can be serialized by anyone into a small object
     * (via Java serialization or XStream),
     * and upon deserialization it reconnects itself with other states persisted elsewhere.
     *
     * @see StepContext
     */
    ANYWHERE,
    /**
     * A class gets persisted in {@linkplain CpsFlowExecution#getProgramDataFile() program data file}
     * along with all the objects used in the user's CPS transformed program.
     *
     * The persistence is through JBoss river protocol, which is a variant of Java serialization.
     */
    PROGRAM,
    /**
     * A class gets persisted through {@link FlowNode} (for example in action) via XStream.
     */
    FLOW_NODE,
    /**
     * A class does not get persisted anywhere. It's only used in memory.
     */
    NONE,
}
