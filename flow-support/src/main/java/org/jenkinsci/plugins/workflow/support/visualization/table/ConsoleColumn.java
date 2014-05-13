package org.jenkinsci.plugins.workflow.support.visualization.table;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.visualization.table.FlowNodeViewColumn;
import org.jenkinsci.plugins.workflow.visualization.table.FlowNodeViewColumnDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
public class ConsoleColumn extends FlowNodeViewColumn {
    @DataBoundConstructor
    public ConsoleColumn() {
    }

    @Override
    public String getColumnCaption() {
        return "";  // no caption needed because icon is clear enough
    }

    @Extension
    public static class DescriptorImpl extends FlowNodeViewColumnDescriptor {
        @Override
        public FlowNodeViewColumn getDefaultInstance() {
            return new ConsoleColumn();
        }

        @Override
        public String getDisplayName() {
            return "Console Output";
        }
    }
}
