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

import hudson.Extension;
import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsFlowFactoryAction2;
import org.jenkinsci.plugins.workflow.cps.steps.LoadStepExecution;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;

/**
 * Attached to a run that is a replay of an earlier one.
 */
class ReplayFlowFactoryAction extends InvisibleAction implements CpsFlowFactoryAction2, Queue.QueueAction {

    private static final Logger LOGGER = Logger.getLogger(ReplayFlowFactoryAction.class.getName());

    private String replacementMainScript;
    private final Map<String,String> replacementLoadedScripts;
    private transient final boolean sandbox;
    
    ReplayFlowFactoryAction(@Nonnull String replacementMainScript, @Nonnull Map<String,String> replacementLoadedScripts, boolean sandbox) {
        this.replacementMainScript = replacementMainScript;
        this.replacementLoadedScripts = new HashMap<String,String>(replacementLoadedScripts);
        this.sandbox = sandbox;
    }

    @Override public CpsFlowExecution create(FlowDefinition def, FlowExecutionOwner owner, List<? extends Action> actions) throws IOException {
        String script = replacementMainScript;
        replacementMainScript = null; // minimize build.xml size
        return new CpsFlowExecution(script, sandbox, owner);
    }

    @Override public boolean shouldSchedule(List<Action> actions) {
        return true; // do not coalesce
    }

    @Extension public static class ReplacerImpl implements LoadStepExecution.Replacer {

        @Override public String replace(String text, CpsFlowExecution execution, String clazz, TaskListener listener) {
            try {
                Queue.Executable executable = execution.getOwner().getExecutable();
                if (executable instanceof Run) {
                    ReplayFlowFactoryAction action = ((Run) executable).getAction(ReplayFlowFactoryAction.class);
                    if (action != null) {
                        String newText = action.replacementLoadedScripts.remove(clazz);
                        if (newText != null) {
                            listener.getLogger().println("Replacing Groovy text with edited version");
                            return newText;
                        } else {
                            listener.getLogger().println("Warning: no replacement Groovy text found for " + clazz);
                        }
                    } else {
                        LOGGER.log(Level.FINE, "{0} was not a replay", executable);
                    }
                } else {
                    LOGGER.log(Level.FINE, "{0} was not a run at all", executable);
                }
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, null, x);
            }
            return text;
        }


    }

}
