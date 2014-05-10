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

package org.jenkinsci.plugins.workflow.support.storage;

import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowActionStorage;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import java.io.IOException;
import javax.annotation.CheckForNull;

/**
 * Abstraction of various ways to persist {@link FlowNode}, for those {@link FlowExecution}s
 * who wants to store them within Jenkins.
 *
 * A flow graph has a characteristic that it's additive.
 *
 * This is clearly a useful internal abstraction to decouple {@link FlowDefinition} implementation
 * from storage mechanism, but not sure if this should be exposed to users.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class FlowNodeStorage implements FlowActionStorage {
    /**
     *
     * @return null
     *      If no node of the given ID has been persisted before.
     */
    public abstract @CheckForNull FlowNode getNode(String id) throws IOException;
    public abstract void storeNode(FlowNode n) throws IOException;
}
