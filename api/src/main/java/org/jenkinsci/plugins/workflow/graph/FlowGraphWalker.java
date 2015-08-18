package org.jenkinsci.plugins.workflow.graph;

import org.jenkinsci.plugins.workflow.flow.FlowExecution;

import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.Stack;

/**
 * Visits a graph of flow nodes and iterates nodes in them.
 * @author Kohsuke Kawaguchi
 */
// TODO guarantee DFS order (is it not already in DFS order?)
public class FlowGraphWalker implements Iterable<FlowNode> {
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
     * @deprecated This class is {@link Iterable} now. Use {@link #iterator()} instead.
     */
    @Deprecated
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

    /**
     * Unlike {@link Iterable#iterator()}, this may be iterated only once.
     */
    @Override
    public Iterator<FlowNode> iterator() {
        return new FlowGraphWalkerIterator();
    }

    private class FlowGraphWalkerIterator implements Iterator<FlowNode> {

        private FlowNode next;
        private int expectedCount;

        public FlowGraphWalkerIterator() {
            expectedCount = q.size();
            next = getNext();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public FlowNode next() {
            FlowNode temp = next;
            next = getNext();
            return temp;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private FlowNode getNext() {
            checkForComodification();
            while (!q.isEmpty()) {
                FlowNode n = q.pop();
                if (!visited.add(n)) {
                    continue;
                }

                List<FlowNode> parents = n.getParents();
                addHeads(parents);
                expectedCount = q.size();
                return n;
            }
            return null;
        }

        private final void checkForComodification() {
            if (q.size() != expectedCount) {
                throw new ConcurrentModificationException();
            }
        }
    }
}
