package org.jenkinsci.plugins.workflow.support.pickles.serialization;

import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;

import java.io.Serializable;

/**
 * Replaces {@link FlowExecutionOwner} in the persisted object graph.
 *
 * <p>
 * This allows the program state to be restored with the "correct" {@link FlowExecutionOwner}
 * that's implicit by the context in which the read-back happens.
 *
 * @author Kohsuke Kawaguchi
 * @see RiverReader#owner
 * @see RiverWriter#owner
 */
public class DryOwner implements Serializable {

    private static final long serialVersionUID = 1L;
}
