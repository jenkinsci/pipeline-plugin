package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Outcome;

/**
 * Indicates the next step after the {@link ThreadTask} has run.
 *
 * @author Kohsuke Kawaguchi
 * @see ThreadTask
 */
public class ThreadTaskResult {
    Outcome resume;
    Outcome suspend;

    private ThreadTaskResult(Outcome resume, Outcome suspend) {
        this.resume = resume;
        this.suspend = suspend;
    }

    public static ThreadTaskResult resumeWith(Outcome o) {
        return new ThreadTaskResult(o,null);
    }

    public static ThreadTaskResult suspendWith(Outcome o) {
        return new ThreadTaskResult(null,o);
    }
}
