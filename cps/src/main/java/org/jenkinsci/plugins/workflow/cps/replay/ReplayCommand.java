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

package org.jenkinsci.plugins.workflow.cps.replay;

import hudson.AbortException;
import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.cli.handlers.GenericItemOptionHandler;
import hudson.model.Job;
import hudson.model.Run;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.cps.replay.Messages;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.Setter;

@Extension public class ReplayCommand extends CLICommand {

    @Argument(required=true, index=0, metaVar="JOB", usage="Name of the job to replay.", handler=JobHandler.class)
    public Job<?,?> job;

    @Option(name="-n", aliases="--number", metaVar="BUILD#", usage="Build to replay, if not the last.")
    public int number;

    @Option(name="-s", aliases="--script", metaVar="SCRIPT", usage="Name of script to edit, such as Script3, if not the main Jenkinsfile.")
    public String script;

    @Override public String getName() {
        return "replay-pipeline";
    }

    @Override public String getShortDescription() {
        return Messages.ReplayCommand_shortDescription();
    }

    @Override protected int run() throws Exception {
        Run<?,?> run = number == 0 ? job.getLastBuild() : job.getBuildByNumber(number);
        if (run == null) {
            throw new AbortException("No such build");
        }
        ReplayAction action = run.getAction(ReplayAction.class);
        if (action == null) {
            // Ideally this would be handled by the OptionHandler (rather than a generic JobHandler),
            // but that means duplicating some code from GenericItemOptionHandler,
            // which currently has no protected method allowing getItemByFullName to be replaced.
            throw new AbortException("Not a Pipeline build");
        }
        if (!action.isEnabled()) {
            throw new AbortException("Not authorized to replay builds of this job");
        }
        String text = IOUtils.toString(stdin);
        if (script != null) {
            Map<String,String> replacementLoadedScripts = new HashMap<String,String>(action.getOriginalLoadedScripts());
            if (!replacementLoadedScripts.containsKey(script)) {
                throw new AbortException("Unrecognized script name among " + replacementLoadedScripts.keySet());
            }
            replacementLoadedScripts.put(script, text);
            action.run(action.getOriginalScript(), replacementLoadedScripts);
        } else {
            action.run(text, action.getOriginalLoadedScripts());
        }
        return 0;
    }

    @SuppressWarnings("rawtypes")
    public static class JobHandler extends GenericItemOptionHandler<Job> {
        
        public JobHandler(CmdLineParser parser, OptionDef option, Setter<Job> setter) {
            super(parser, option, setter);
        }

        @Override protected Class<Job> type() {
            return Job.class;
        }

    }

}
