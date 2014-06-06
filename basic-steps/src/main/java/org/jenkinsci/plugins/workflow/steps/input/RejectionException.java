package org.jenkinsci.plugins.workflow.steps.input;

import hudson.model.User;

/**
 * Indicates that the input step was rejected by the user.
 *
 * @author Kohsuke Kawaguchi
 */
public class RejectionException extends Exception {
    private final String userName;
    private final long timestamp;

    public RejectionException(User u) {
        super("Rejected by "+u);
        this.userName = u==null ? null : u.getId();
        this.timestamp = System.currentTimeMillis();
        // TODO: verify stack trace fixup with CPS
    }

    /**
     * Gets the user who rejected this.
     */
    public User getUser() {
        return User.get(userName);
    }

    /**
     * Gets the timestamp when the rejection occurred.
     */
    public long getTimestamp() {
        return timestamp;
    }
}
