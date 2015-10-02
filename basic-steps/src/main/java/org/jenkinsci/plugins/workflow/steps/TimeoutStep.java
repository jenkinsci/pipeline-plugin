package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * Executes the body with a timeout, which will kill the body.
 *
 * @author Kohsuke Kawaguchi
 */
public class TimeoutStep extends AbstractStepImpl implements Serializable {

    private final int time;

    private TimeUnit unit = TimeUnit.MINUTES;

    @DataBoundConstructor
    public TimeoutStep(int time) {
        this.time = time;
    }

    @DataBoundSetter
    public void setUnit(TimeUnit unit) {
        this.unit = unit;
    }

    public int getTime() {
        return time;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(TimeoutStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "timeout";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Enforce time limit";
        }

        public ListBoxModel doFillUnitItems() {
            ListBoxModel r = new ListBoxModel();
            for (TimeUnit unit : TimeUnit.values()) {
                r.add(unit.name());
            }
            return r;
        }

    }

    private static final long serialVersionUID = 1L;
}
