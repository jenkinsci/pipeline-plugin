/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.job.console;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;

import hudson.model.BuildListener;

/**
 * Console logger used to write workflow related metadata in console text.
 *
 * It wraps the regular {@link BuildListener} to add a filter that annotates any text line sent to the console through
 * this logger. It also adds a prefix to the line ([Pipeline]).
 *
 * Annotated lines will be rendered in a lighter color so they do not interefere with the important part of the log.
 */
public class WorkflowConsoleLogger {

    private final BuildListener listener;
    private final WorkflowMetadataConsoleFilter annotator;

    public WorkflowConsoleLogger(BuildListener listener) {
        this.listener = listener;
        this.annotator = new WorkflowMetadataConsoleFilter(listener.getLogger());
    }

    /**
     * Provides access to the wrapped listener logger.
     * @return the logger print stream
     */
    public PrintStream getLogger() {
        return listener.getLogger();
    }

    /**
     * Sends an annotated log message to the console after adding the prefix [Workflow].
     * @param message the message to wrap and annotate.
     */
    public void log(String message) {
        logAnnot(WorkflowRunConsoleNote.CONSOLE_NOTE_PREFIX, message);
    }

    private void logAnnot(String prefix, String message) {
        byte[] msg = String.format("%s%s%n", prefix, message).getBytes(Charset.defaultCharset());
        try {
            annotator.eol(msg, msg.length);
        } catch (IOException e) {
            listener.getLogger().println("Problem with writing into console log: " + e.getMessage());
        }
    }
}
