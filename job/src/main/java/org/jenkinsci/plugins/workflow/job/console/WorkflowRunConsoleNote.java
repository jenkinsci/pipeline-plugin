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

import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import hudson.MarkupText;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import hudson.model.Run;

/**
 * Console note for Workflow metadata specific messages.
 * See {@link WorkflowConsoleLogger} for more information.
 */
public class WorkflowRunConsoleNote extends ConsoleNote<Run<?, ?>> {

    /**
     * Prefix used in metadata lines.
     */
    public static final String CONSOLE_NOTE_PREFIX = "[Workflow] ";

    /**
     * CSS color selector.
     */
    private static final String TEXT_COLOR = "9A9999";

    private static final String START_NOTE = "<span style=\"color:#"+ TEXT_COLOR +"\">";
    private static final String END_NOTE = "</span>";

    @Override
    public ConsoleAnnotator<Run<?,?>> annotate(Run<?, ?> context, MarkupText text, int charPos) {
        if (context instanceof WorkflowRun) {
            if (text.getText().startsWith(CONSOLE_NOTE_PREFIX)) {
                text.addMarkup(0, text.length(), START_NOTE, END_NOTE);
            }
        }
        return null;
    }

    private static final long serialVersionUID = 1L;

}
