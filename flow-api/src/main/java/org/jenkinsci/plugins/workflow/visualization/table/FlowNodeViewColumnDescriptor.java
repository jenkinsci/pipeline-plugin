package org.jenkinsci.plugins.workflow.visualization.table;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.kohsuke.accmod.Restricted;

import javax.annotation.CheckForNull;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * To enable rendering a table of {@link FlowNode} without the user explicitly configuring
     * columns, this method provides a default instance.
     *
     * If column requires some configuration and no sensible default instance exists, return null.
     *
     * When more columns get written, this concept will likely break down. Revisit this.
     *
     * @deprecated
     *      Don't use this method outside the core workflow plugins as we'll likely change this.
     */
    public @CheckForNull FlowNodeViewColumn getDefaultInstance() {
        return null;
    }

    public static DescriptorExtensionList<FlowNodeViewColumn,FlowNodeViewColumnDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(FlowNodeViewColumn.class);
    }

    /**
     * @deprecated
     *      Don't use this method outside the core workflow plugins as we'll likely change this.
     */
    public static List<FlowNodeViewColumn> getDefaultInstances() {
        List<FlowNodeViewColumn> r = new ArrayList<FlowNodeViewColumn>();
        for (FlowNodeViewColumnDescriptor d : all()) {
            FlowNodeViewColumn c = d.getDefaultInstance();
            if (c!=null)
                r.add(c);
        }
        return r;
    }
}
