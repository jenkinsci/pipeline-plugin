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

package org.jenkinsci.plugins.workflow;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import org.codehaus.groovy.transform.ASTTransformationVisitor;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MemoryAssert;

public class CpsFlowExecutionTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    
    private static WeakReference<ClassLoader> LOADER;
    public static void register(Object o) {
        LOADER = new WeakReference<ClassLoader>(o.getClass().getClassLoader());
    }
    @Test public void loaderReleased() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(CpsFlowExecutionTest.class.getName() + ".register(this)"));
        r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertNotNull(LOADER);
        System.err.println(LOADER.get());
        {
            // TODO in Groovy 1.8.9 this keeps static state, but only for the last script (as also noted in JENKINS-23762).
            // The fix of GROOVY-5025 (62bfb68) in 1.9 addresses this, which we would get if JENKINS-21249 is implemented.
            Field f = ASTTransformationVisitor.class.getDeclaredField("compUnit");
            f.setAccessible(true);
            f.set(null, null);
        }
        MemoryAssert.assertGC(LOADER);
    }

    /* Failed attempt to make the test print soft references it has trouble clearing. The test ultimately passes, but cannot find the soft references via any root path.
    private static void assertGC(WeakReference<?> reference) throws Exception {
        assertTrue(true); reference.get(); // preload any needed classes!
        Set<Object[]> objects = new HashSet<Object[]>();
        int size = 1024;
        while (reference.get() != null) {
            LiveEngine e = new LiveEngine();
            // The default filter, ScannerUtils.skipNonStrongReferencesFilter(), omits SoftReference.referent that we care about.
            // The constructor accepting a filter ANDs it with the default filter, making it useless for this purpose.
            Field f = LiveEngine.class.getDeclaredField("filter");
            f.setAccessible(true);
            f.set(e, new Filter() {
                final Field referent = Reference.class.getDeclaredField("referent");
                @Override public boolean accept(Object obj, Object referredFrom, Field reference) {
                    return !(referent.equals(reference) && referredFrom instanceof WeakReference);
                }
            });
            System.err.println(e.trace(Collections.singleton(reference.get()), null));
            System.err.println("allocating " + size);
            try {
                objects.add(new Object[size]);
            } catch (OutOfMemoryError ignore) {
                break;
            }
            size *= 1.1;
            System.gc();
        }
        objects = null;
        System.gc();
        Object obj = reference.get();
        if (obj != null) {
            fail(LiveReferences.fromRoots(Collections.singleton(obj)).toString());
        }
    }
    */

}
