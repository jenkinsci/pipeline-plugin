package org.jenkinsci.plugins.workflow.cps.steps;

/**
 * Indicates a failure in one of the sub-workflows of {@code parallel} task.
 *
 * <pre>{@code
 * parallel (
 *    a : { ... },
 *    b : {
 *        throw new IllegalArgumentException();
 *    }
 * )  // <-- the invocation of parallel will fail with ParallelStepException that wraps IllegalArgumentException
 * }</pre>
 *
 * @author Kohsuke Kawaguchi
 */
public class ParallelStepException extends RuntimeException {
    private final String name;

    public ParallelStepException(String name, Throwable cause) {
        super("Parallel step "+name+" failed", cause);
        this.name = name;
    }

    /**
     * Gets the name of the parallel step that has failed.
     *
     * In the class javadoc example, this method returns "b", indicating it is that branch
     * that has failed.
     */
    public String getName() {
        return name;
    }
}
