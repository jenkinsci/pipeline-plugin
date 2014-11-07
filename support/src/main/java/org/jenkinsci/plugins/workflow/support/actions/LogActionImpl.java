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

package org.jenkinsci.plugins.workflow.support.actions;

import com.google.common.base.Charsets;
import org.apache.commons.jelly.XMLOutput;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.actions.FlowNodeAction;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import hudson.console.AnnotatedLargeText;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

/**
 * {@link LogAction} implementation that stores per-node log file under {@link FlowExecutionOwner#getRootDir()}.
 *
 * @author Kohsuke Kawaguchi
 */
public class LogActionImpl extends LogAction implements FlowNodeAction {
    private transient FlowNode parent;
    private transient volatile File log;
    private String charset;

    public LogActionImpl(FlowNode parent, Charset charset) {
        if (!parent.isRunning()) {
            throw new IllegalStateException("cannot start writing logs to a finished node " + parent);
        }
        this.parent = parent;
        this.charset = charset.name();
    }

    public FlowNode getParent() {
        return parent;
    }

    @Override
    public AnnotatedLargeText<? extends FlowNode> getLogText() {
        try {
            getLogFile();
            if (!log.exists())
                return new AnnotatedLargeText<FlowNode>(new ByteBuffer(), getCharset(), !parent.isRunning(), parent);

            return new AnnotatedLargeText<FlowNode>(log, getCharset(), !parent.isRunning(), parent);
        } catch (IOException e) {
            ByteBuffer buf = new ByteBuffer();
            PrintStream ps;
            try {
                ps = new PrintStream(buf, false, "UTF-8");
            } catch (UnsupportedEncodingException x) {
                throw new AssertionError(x);
            }
            ps.println("Failed to find log file for id="+parent.getId());
            e.printStackTrace(ps);
            ps.close();
            return new AnnotatedLargeText<FlowNode>(buf, Charsets.UTF_8, true, parent);
        }
    }

    /**
     * The actual log file.
     */
    public File getLogFile() throws IOException {
        if (log==null)
            log = new File(parent.getExecution().getOwner().getRootDir(), parent.getId() + ".log");
        return log;
    }

    /**
     * Used from <tt>console.jelly</tt> to write annotated log to the given output.
     */
    public void writeLogTo(long offset, XMLOutput out) throws IOException {
        AnnotatedLargeText l = getLogText();
        if (l!=null)
            l.writeHtmlTo(offset, out.asWriter());
    }

    @Override
    public void onLoad(FlowNode parent) {
        this.parent = parent;
    }

    public Charset getCharset() {
        if(charset==null)   return Charset.defaultCharset();    // just being defensive
        return Charset.forName(charset);
    }

    @Override public String toString() {
        return "LogActionImpl[" + log + "]";
    }
}
