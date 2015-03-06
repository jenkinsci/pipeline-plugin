/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

package org.jenkinsci.plugins.workflow.support.steps.build;

import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class BuildTriggerStepTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-26692")
    @Test public void configRoundTrip() throws Exception {
        BuildTriggerStep s = new BuildTriggerStep("ds");
        s = new StepConfigTester(r).configRoundTrip(s);
        assertEquals(null, s.getQuietPeriod());
        assertTrue(s.isPropagate());
        s.setPropagate(false);
        s.setQuietPeriod(5);
        s = new StepConfigTester(r).configRoundTrip(s);
        assertEquals(Integer.valueOf(5), s.getQuietPeriod());
        assertFalse(s.isPropagate());
        s.setQuietPeriod(0);
        s = new StepConfigTester(r).configRoundTrip(s);
        assertEquals(Integer.valueOf(0), s.getQuietPeriod());
    }

}