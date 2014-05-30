package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
import hudson.model.Node;
import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class AbstractStepImplTest extends Assert {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Inject
    BogusStep.DescriptorImpl d;

    @Test
    public void inject() throws Exception {
        j.getInstance().getInjector().injectMembers(this);

        Map r = new HashMap();
        r.put("a",3);
        r.put("b","bbb");
        r.put("c",null);
        r.put("d","ddd");
        BogusStep step = (BogusStep) d.newInstance(r);

        assertEquals(step.a,3);
        assertEquals(step.b,"bbb");
        assertNull(step.c);
        assertEquals(step.d,"ddd");

        StepContext c = mock(StepContext.class);
        when(c.get(Node.class)).thenReturn(j.getInstance());

        step.start(c);
    }

    public static class BogusStep extends AbstractStepImpl {
        int a;
        String b;
        String c;
        Object d;

        @DataBoundConstructor
        public BogusStep(int a, String b) {
            this.a = a;
            this.b = b;
        }

        @DataBoundSetter
        void setSomething(String c, Object d) {
            this.c = c;
            this.d = d;
        }

        @Inject
        Jenkins jenkins;

        @StepContextParameter
        Node n;

        @Override
        protected boolean doStart(StepContext context) {
            Assert.assertSame(jenkins, Jenkins.getInstance());
            Assert.assertSame(n, Jenkins.getInstance());
            return true;
        }

        @Extension
        public static class DescriptorImpl extends AbstractStepImpl.DescriptorImpl {
            @Override
            public String getFunctionName() {
                return "fff";
            }

            @Override
            public String getDisplayName() {
                return "ggg";
            }
        }
    }
}
