/*
 * The MIT License
 *
 * Copyright (c) 2015 CloudBees, Inc.
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
package org.jenkinsci.plugins.workflow.flow;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

/**
 * Listens executions and {@link FlowNode} status changes.
 * @author Oleg Nenashev
 * @since TODO
 */
public abstract class FlowListener implements ExtensionPoint {
    
    private final static Logger LOGGER = Logger.getLogger(FlowListener.class.getName());
    
    /**
     * Notifies that a new execution node has been added to the flow.
     * {@link FlowExecution} should batch up changes to a group and call this once, 
     * as opposed to call this for every new node added.
     *
     * One of the use cases of this listener is to persist the state of {@link FlowExecution}.
     * @param node Newly created node
     */
    public void onNewHead(@Nonnull FlowNode node) {
        // Do nothing by default
    }
    
    /**
     * Indicates that the execution of the {@link FlowNode} has been started.
     * @param stepExecution Step execution
     */
    public void onStepExecutionStarted(@Nonnull StepExecution stepExecution) {
        // Do nothing by default
    }
    
    /**
     * Indicates that the execution of the {@link FlowNode} body has been started.
     * It can be used to catch nested calls.
     * @param bodyExecution Body execution
     */
    public void onBodyExecutionStarted(@Nonnull BodyExecution bodyExecution) {
        // Do nothing by default
    }
    
    
    /**
     * Notifies all listeners about the new flow node creation.
     * @param node Newly created node
     */
    public static void fireNewHead(@Nonnull FlowNode node) {
        for (FlowListener listener : all()) {
            try {
                listener.onNewHead(node);
            } catch (Exception ex) { // Prevent failures on runtime exceptions
                LOGGER.log(Level.SEVERE, "Runtime exception during the flow execution callback processing: " + listener, ex);
            }
        }
    }
    
    /**
     * Notifies all listeners that the FlowNode has been started.
     * @param stepExecution Step Execution
     */
    public static void fireStepExecutionStarted(@Nonnull StepExecution stepExecution) {
        for (FlowListener listener : all()) {
            try {
                listener.onStepExecutionStarted(stepExecution);
            } catch (Exception ex) { // Prevent failures on runtime exceptions
                LOGGER.log(Level.SEVERE, "Runtime exception during the flow execution callback processing: " + listener, ex);
            }
        }
    }
    
    /**
     * Notifies all listeners that the FlowNode has been started.
     * It indicates that {@link BodyInvoker} triggered the start method, but actually 
     * @param bodyExecution Body Execution
     */
    public static void fireBodyExecutionStarted(@Nonnull BodyExecution bodyExecution) {
        for (FlowListener listener : all()) {
            try {
                listener.onBodyExecutionStarted(bodyExecution);
            } catch (Exception ex) { // Prevent failures on runtime exceptions
                LOGGER.log(Level.SEVERE, "Runtime exception during the body execution callback processing: " + listener, ex);
            }
        }
    }
    
    /**
     * Retrieves a list of all {@link FlowListener} extension implementations.
     * @return A list of all {@link FlowListener} extension implementations.
     */
    public static @Nonnull ExtensionList<FlowListener> all() {
        return ExtensionList.lookup(FlowListener.class);
    }
}
