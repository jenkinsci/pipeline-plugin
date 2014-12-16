package org.jenkinsci.plugins.workflow.graph;

import org.jenkinsci.plugins.workflow.flow.FlowExecution;

import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.Stack;

/**
 * Visits a graph of flow nodes and iterates nodes in them.
 * @author Kohsuke Kawaguchi
 */
// TODO guarantee DFS order (is it not already in DFS order?)
// TODO implement Iterable<FlowNode>
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

    public void addHeads(List<FlowNode> heads) {
        ListIterator<FlowNode> itr = heads.listIterator(heads.size());
        while (itr.hasPrevious())
            q.add(itr.previous());
    }

    /**
     * Each time this method is called, it returns a new node until all the nodes are visited,
     * in which case this method returns null.
     */
    public FlowNode next() {
        while (!q.isEmpty()) {
            FlowNode n = q.pop();
            if (!visited.add(n))
                continue;

            addHeads(n.getParents());
            return n;
        }
        return null;
    }
}
