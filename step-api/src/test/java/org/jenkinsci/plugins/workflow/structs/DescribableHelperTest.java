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

package org.jenkinsci.plugins.workflow.structs;

import hudson.Extension;
import hudson.Main;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class DescribableHelperTest {

    @BeforeClass public static void isUnitTest() {
        Main.isUnitTest = true; // suppress HsErrPidList
    }

    @Test public void instantiate() throws Exception {
        Map<String,Object> args = new HashMap<String,Object>();
        args.put("text", "hello");
        args.put("flag", true);
        args.put("ignored", "!");
        assertEquals("C:hello/true", DescribableHelper.instantiate(C.class, args).toString());
        args.put("value", "main");
        assertEquals("I:main/hello/true", DescribableHelper.instantiate(I.class, args).toString());
        args.clear();
        args.put("text", "goodbye");
        assertEquals("C:goodbye/false", DescribableHelper.instantiate(C.class, args).toString());
    }

    @Test public void uninstantiate() throws Exception {
        assertEquals("{flag=true, shorty=0, text=stuff}", DescribableHelper.uninstantiate(new C("stuff", true)).toString());
        I i = new I("stuff");
        i.setFlag(true);
        i.text = "more";
        assertEquals("{flag=true, text=more, value=stuff}", DescribableHelper.uninstantiate(i).toString());
    }

    @Test public void mismatchedTypes() throws Exception {
        try {
            DescribableHelper.instantiate(I.class, Collections.singletonMap("value", 99));
            fail();
        } catch (ClassCastException x) {
            String message = x.getMessage();
            assertTrue(message, message.contains(I.class.getName()));
            assertTrue(message, message.contains("value"));
            assertTrue(message, message.contains("java.lang.String"));
            assertTrue(message, message.contains("java.lang.Integer"));
        }
    }

    public static final class C {
        public final String text;
        private final boolean flag;
        @DataBoundConstructor public C(String text, boolean flag) {
            this.text = text;
            this.flag = flag;
        }
        public boolean isFlag() {
            return flag;
        }
        @Override public String toString() {
            return "C:" + text + "/" + flag;
        }
        // Are not actually trying to inject it; just making sure that unhandled @DataBoundSetter types are ignored if unused.
        public short getShorty() {return 0;}
        @DataBoundSetter public void setShorty(short s) {throw new UnsupportedOperationException();}
    }

    public static final class I {
        private final String value;
        @DataBoundSetter private String text;
        private boolean flag;
        @DataBoundConstructor public I(String value) {
            this.value = value;
        }
        public String getValue() {
            return value;
        }
        public String getText() {
            return text;
        }
        public boolean isFlag() {
            return flag;
        }
        @DataBoundSetter public void setFlag(boolean f) {
            this.flag = f;
        }
        @Override public String toString() {
            return "I:" + value + "/" + text + "/" + flag;
        }
    }

    @SuppressWarnings("unchecked")

    @Test public void findSubtypes() throws Exception {
        assertEquals(new HashSet<Class<?>>(Arrays.asList(Impl1.class, Impl2.class)), DescribableHelper.findSubtypes(Base.class));
        assertEquals(Collections.singleton(Impl1.class), DescribableHelper.findSubtypes(Marker.class));
    }

    @Test public void bindMapsFQN() throws Exception {
        Map<String,Object> impl1 = new HashMap<String,Object>();
        impl1.put("$class", Impl1.class.getName());
        impl1.put("text", "hello");
        assertEquals("UsesBase[Impl1[hello]]", DescribableHelper.instantiate(UsesBase.class, Collections.singletonMap("base", impl1)).toString());
    }

    @Test public void bindMapsSimpleName() throws Exception {
        Map<String,Object> impl1 = new HashMap<String,Object>();
        impl1.put("$class", "Impl1");
        impl1.put("text", "hello");
        assertEquals("UsesBase[Impl1[hello]]", DescribableHelper.instantiate(UsesBase.class, Collections.singletonMap("base", impl1)).toString());
    }

    @Test public void bindMapsImplicitName() throws Exception {
        assertEquals("UsesImpl2[Impl2[true]]", DescribableHelper.instantiate(UsesImpl2.class, Collections.singletonMap("impl2", Collections.singletonMap("flag", true))).toString());
    }

    @Test public void nestedDataBoundSetter() throws Exception {
        Map<String,Object> impl2 = new HashMap<String,Object>();
        impl2.put("$class", "Impl2");
        assertEquals("UsesBase[Impl2[false]]", DescribableHelper.instantiate(UsesBase.class, Collections.singletonMap("base", impl2)).toString());
        impl2.put("flag", true);
        assertEquals("UsesBase[Impl2[true]]", DescribableHelper.instantiate(UsesBase.class, Collections.singletonMap("base", impl2)).toString());
    }

    @Test public void nestedUninstantiate() throws Exception {
        assertEquals("{base={$class=Impl1, text=hello}}", DescribableHelper.uninstantiate(new UsesBase(new Impl1("hello"))).toString());
        assertEquals("{base={$class=Impl2, flag=false}}", DescribableHelper.uninstantiate(new UsesBase(new Impl2())).toString());
        Impl2 impl2 = new Impl2();
        impl2.setFlag(true);
        assertEquals("{impl2={flag=true}}", DescribableHelper.uninstantiate(new UsesImpl2(impl2)).toString());
        // TODO also check case that a FQN is needed
    }

    // TODO instantiate/uninstantiate of URL, Enum, Character

    public static class UsesBase {
        public final Base base;
        @DataBoundConstructor public UsesBase(Base base) {
            this.base = base;
        }
        @Override public String toString() {
            return "UsesBase[" + base + "]";
        }
    }

    public static class UsesImpl2 {
        public final Impl2 impl2;
        @DataBoundConstructor public UsesImpl2(Impl2 impl2) {
            this.impl2 = impl2;
        }
        @Override public String toString() {
            return "UsesImpl2[" + impl2 + "]";
        }
    }

    public static abstract class Base extends AbstractDescribableImpl<Base> {}

    public interface Marker {}

    public static final class Impl1 extends Base implements Marker {
        private final String text;
        @DataBoundConstructor public Impl1(String text) {
            this.text = text;
        }
        public String getText() {
            return text;
        }
        @Override public String toString() {
            return "Impl1[" + text + "]";
        }
        @Extension public static final class DescriptorImpl extends Descriptor<Base> {
            @Override public String getDisplayName() {
                return "Impl1";
            }
        }
    }

    public static final class Impl2 extends Base {
        private boolean flag;
        @DataBoundConstructor public Impl2() {}
        public boolean isFlag() {
            return flag;
        }
        @DataBoundSetter public void setFlag(boolean flag) {
            this.flag = flag;
        }
        @Override public String toString() {
            return "Impl2[" + flag + "]";
        }
        @Extension public static final class DescriptorImpl extends Descriptor<Base> {
            @Override public String getDisplayName() {
                return "Impl2";
            }
        }
    }

}