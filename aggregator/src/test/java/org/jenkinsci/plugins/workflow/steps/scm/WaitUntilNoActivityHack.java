/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.steps.scm;

import hudson.model.Computer;
import hudson.model.Job;
import java.util.Arrays;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Suspect a race condition in {@link JenkinsRule#waitUntilNoActivity}.
 */
class WaitUntilNoActivityHack {

    static void waitUntilNoActivity(Job<?,?> job, int expectedNumber, JenkinsRule rule) throws Exception {
        diagnosis("before waitUntilNoActivity", rule);
        rule.waitUntilNoActivity();
        diagnosis("after waitUntilNoActivity", rule);
        int number1 = job.getLastBuild().number;
        if (number1 != expectedNumber) {
            Thread.sleep(5000);
            diagnosis("after an extra sleep", rule);
            int number2 = job.getLastBuild().number;
            if (number1 != number2) {
                throw new IllegalStateException("and that made the difference: " + number1 + " → " + number2);
            }
        }
    }

    private static void diagnosis(String when, JenkinsRule rule) {
        Computer c = rule.jenkins.toComputer();
        System.err.println(when + ": " + Arrays.asList(rule.jenkins.getQueue().getItems()) + " " + (c != null ? c.getOneOffExecutors() : null));
    }

    private WaitUntilNoActivityHack() {}

}
