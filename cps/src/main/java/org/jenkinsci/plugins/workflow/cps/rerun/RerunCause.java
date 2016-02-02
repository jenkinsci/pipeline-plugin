/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.cps.rerun;

import hudson.console.ModelHyperlinkNote;
import hudson.model.Cause;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import javax.annotation.CheckForNull;

/**
 * Marker that a run is a rerun of an earlier one.
 */
public class RerunCause extends Cause {

    private final int number;
    private transient Job<?,?> job;

    RerunCause(Run<?,?> run) {
        this.number = run.getNumber();
        job = run.getParent();
    }

    @Override public void onLoad(Run<?,?> build) {
        super.onLoad(build);
        job = build.getParent();
    }
    
    public int getNumber() {
        return number;
    }

    public @CheckForNull Run<?,?> getOriginal() {
        return job.getBuildByNumber(number);
    }

    @Override public String getShortDescription() {
        return "Reran #" + number;
    }

    @Override public void print(TaskListener listener) {
        Run<?,?> original = getOriginal();
        if (original != null) {
            listener.getLogger().println("Reran " + ModelHyperlinkNote.encodeTo(original));
        } else {
            super.print(listener); // same, without hyperlink
        }
    }

}
