package org.jenkinsci.plugins.workflow.job.console;

import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import hudson.model.Run;

public class WorkflowRunConsoleNote extends ConsoleNote<Run<?, ?>> {

    @Override
    public ConsoleAnnotator<Run<?,?>> annotate(Run<?, ?> context, MarkupText text, int charPos) {
        if (context instanceof WorkflowRun) {
            // TODO
        }
        return null;
    }

    @Extension
    public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {
        public String getDisplayName() {
            return "Workflow Console Note";
        }
    }

    private static final long serialVersionUID = 1L;

}
