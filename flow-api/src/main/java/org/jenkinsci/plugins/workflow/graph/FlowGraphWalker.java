package org.jenkinsci.plugins.workflow.graph;

import org.jenkinsci.plugins.workflow.flow.FlowExecution;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

/**
 * Visits a graph of flow nodes and iterates nodes in them.
 *
 * TODO: guarantee DFS order
 *
 * @author Kohsuke Kawaguchi
 */
public class FlowGraphWalker {
    // queue of nodes to visit.
    // it's a stack and not queue to visit nodes in DFS
    Stack<FlowNode> q = new Stack<FlowNode>();

    Set<FlowNode> visited = new HashSet<FlowNode>();

    public FlowGraphWalker(FlowExecution exec) {
        q.addAll(exec.getCurrentHeads());
    }

    public FlowGraphWalker() {
    }

    public void addHead(FlowNode head) {
        q.add(head);
    }

    public void addHeads(Collection<FlowNode> heads) {
        q.addAll(heads);
    }

    /**
     * Each time this method is called, it returns a new node until all the nodes are visited,
     * in which case this method returns null.
     */
    public FlowNode next() {
        while (!q.isEmpty()) {
            FlowNode n = q.pop();
            if (visited.contains(n))
                continue;

            q.addAll(n.getParents());
            return n;
        }
        return null;
    }
}
