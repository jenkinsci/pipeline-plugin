package org.jenkinsci.plugins.workflow.support.steps.input;

import hudson.model.User;
import java.io.Serializable;
import javax.annotation.CheckForNull;
import jenkins.model.CauseOfInterruption;
import org.kohsuke.stapler.export.Exported;

/**
 * Indicates that the input step was rejected by the user.
 */
public final class Rejection extends CauseOfInterruption {

    private static final long serialVersionUID = 1;

    private final @CheckForNull String userName;
    private final long timestamp;

    public Rejection(@CheckForNull User u) {
        this.userName = u==null ? null : u.getId();
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Gets the user who rejected this.
     */
    @Exported
    public @CheckForNull User getUser() {
        return userName != null ? User.get(userName) : null;
    }

    /**
     * Gets the timestamp when the rejection occurred.
     */
    @Exported
    public long getTimestamp() {
        return timestamp;
    }

    @Override public String getShortDescription() {
        User u = getUser();
        if (u != null) {
            return "Rejected by " + u.getDisplayName();
        } else {
            return "Rejected";
        }
    }

}
