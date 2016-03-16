package org.jenkinsci.plugins.workflow.support.steps;

import static org.junit.Assert.assertEquals;

import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class MilestoneStepTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void configRoundTrip() throws Exception {
        MilestoneStep milestone = new StepConfigTester(r).configRoundTrip(new MilestoneStep(1));
        assertEquals(Integer.valueOf(1), milestone.ordinal);
        milestone = new MilestoneStep(2);

        milestone = new StepConfigTester(r).configRoundTrip(milestone);
        assertEquals(Integer.valueOf(2), milestone.ordinal);
    }

}
