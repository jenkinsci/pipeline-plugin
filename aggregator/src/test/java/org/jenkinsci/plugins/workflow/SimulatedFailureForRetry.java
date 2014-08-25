package org.jenkinsci.plugins.workflow;

/**
 * Used in the test to simulate a failure that we want to catch in the retry block.
 *
 * This is not indicative of a test problem.
 *
 * @author Kohsuke Kawaguchi
 */
public class SimulatedFailureForRetry extends Exception {
}
