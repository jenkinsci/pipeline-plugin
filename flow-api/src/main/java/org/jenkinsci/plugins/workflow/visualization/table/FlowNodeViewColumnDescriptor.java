package org.jenkinsci.plugins.workflow.visualization.table;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * {@link Descriptor} for {@link FlowNodeViewColumn}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class FlowNodeViewColumnDescriptor extends Descriptor<FlowNodeViewColumn> {
    protected FlowNodeViewColumnDescriptor(Class<? extends FlowNodeViewColumn> clazz) {
        super(clazz);
    }

    protected FlowNodeViewColumnDescriptor() {
    }

    public static DescriptorExtensionList<FlowNodeViewColumn,FlowNodeViewColumnDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(FlowNodeViewColumn.class);
    }
}
