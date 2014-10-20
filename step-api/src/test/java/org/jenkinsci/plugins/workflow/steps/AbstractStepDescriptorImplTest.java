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

package org.jenkinsci.plugins.workflow.steps;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;
import org.junit.Test;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class AbstractStepDescriptorImplTest {
    
    @Test public void instantiate() throws Exception {
        Map<String,Object> args = new HashMap<String,Object>();
        args.put("text", "hello");
        args.put("flag", true);
        args.put("ignored", "!");
        assertEquals("C:hello/true", AbstractStepDescriptorImpl.instantiate(C.class, args).toString());
        args.put("value", "main");
        assertEquals("I:main/hello/true", AbstractStepDescriptorImpl.instantiate(I.class, args).toString());
        args.clear();
        args.put("text", "goodbye");
        assertEquals("C:goodbye/false", AbstractStepDescriptorImpl.instantiate(C.class, args).toString());
    }

    @Test public void uninstantiate() throws Exception {
        assertEquals("{flag=true, shorty=0, text=stuff}", AbstractStepDescriptorImpl.uninstantiate(new C("stuff", true)).toString());
        I i = new I("stuff");
        i.setFlag(true);
        i.text = "more";
        assertEquals("{flag=true, text=more, value=stuff}", AbstractStepDescriptorImpl.uninstantiate(i).toString());
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

}