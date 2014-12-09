package org.jenkinsci.plugins.workflow.visualization.table;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.views.ListViewColumnDescriptor;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.kohsuke.stapler.export.Exported;

/**
 * Extension point for adding a column to a table rendering of {@link FlowNode}s.
 *
 * <p>
 * This object must have the <tt>column.groovy</tt>. This view
 * is called for each cell of this column. The {@link FlowNode} object
 * is passed in the "node" variable. The view should render
 * a {@code <td>} tag.
 *
 * <p>
 * This object may have an additional <tt>columnHeader.groovy</tt>. The default column header
 * will render {@link #getColumnCaption()}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.279
 * @see ListViewColumnDescriptor
 */
public class FlowNodeViewColumn extends AbstractDescribableImpl<FlowNodeViewColumn> implements ExtensionPoint {
    /**
     * Returns the name of the column that explains what this column means
     *
     * @return
     *      The convention is to use capitalization like "Foo Bar Zot".
     */
    @Exported
    public String getColumnCaption() {
        return getDescriptor().getDisplayName();
    }

    public FlowNodeViewColumnDescriptor getDescriptor() {
        return (FlowNodeViewColumnDescriptor)super.getDescriptor();
    }
}
