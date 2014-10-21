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

package org.jenkinsci.plugins.workflow.cps;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.structs.DescribableHelper;
import org.kohsuke.stapler.ClassDescriptor;
import org.kohsuke.stapler.NoStaplerConstructorException;

/**
 * Takes a {@link Step} as configured through the UI and tries to produce equivalent Groovy code.
 */
class Snippetizer {
    
    public static String object2Groovy(Object o) throws UnsupportedOperationException {
        Class<? extends Object> clazz = o.getClass();
        for (StepDescriptor d : StepDescriptor.all()) {
            if (d.clazz.equals(clazz)) {
                StringBuilder b = new StringBuilder(d.getFunctionName());
                Map<String,Object> args = new TreeMap<String,Object>(d.defineArguments((Step) o));
                args.values().removeAll(Collections.singleton(null)); // do not write null values
                boolean first = true;
                for (Map.Entry<String,Object> entry : args.entrySet()) {
                    if (first) {
                        first = false;
                        if (d.takesImplicitBlockArgument()) {
                            b.append('(');
                        } else {
                            b.append(' ');
                        }
                    } else {
                        b.append(", ");
                    }
                    String key = entry.getKey();
                    if (args.size() > 1 || !key.equals(AbstractStepDescriptorImpl.KEY_VALUE)) {
                        b.append(key).append(": ");
                    }
                    render(b, entry.getValue());
                }
                if (d.takesImplicitBlockArgument()) {
                    if (!args.isEmpty()) {
                        b.append(')');
                    }
                    b.append(" {\n    // some block\n}");
                }
                return b.toString();
            }
        }
        throw new UnsupportedOperationException("Unknown step " + clazz);
    }
    
    static void render(StringBuilder b, Object value) {
        if (value == null) {
            b.append("null");
            return;
        }
        Class<?> valueC = value.getClass();
        if (valueC == String.class || valueC == Character.class) {
            String text = String.valueOf(value);
            if (text.contains("\n")) {
                b.append('/').append(text.replace("/", "\\/")).append('/');
            } else {
                b.append('\'').append(text.replace("\\", "\\\\").replace("'", "\\'")).append('\'');
            }
        } else if (valueC == Boolean.class || valueC == Integer.class || valueC == Long.class) {
            b.append(value);
        } else if (valueC == URL.class) {
            b.append("new java.net.URL(");
            render(b, ((URL) value).toString());
            b.append(')');
        } else if (value instanceof List || value instanceof Object[]) {
            List<?> list;
            if (value instanceof List) {
                list = (List<?>) value;
            } else {
                list = Arrays.asList((Object[]) value);
            }
            b.append('[');
            boolean first = true;
            for (Object elt : list) {
                if (first) {
                    first = false;
                } else {
                    b.append(", ");
                }
                render(b, elt);
            }
            b.append(']');
        } else if (value instanceof Enum) {
            Enum<?> e = (Enum) value;
            b.append(e.getDeclaringClass().getCanonicalName()).append('.').append(e.name());
        } else {
            try {
                Map<String,Object> args = DescribableHelper.uninstantiate(value);
                b.append("new ").append(valueC.getCanonicalName()).append('(');
                ClassDescriptor d = new ClassDescriptor(valueC);
                boolean first = true;
                for (String name : d.loadConstructorParamNames()) {
                    if (first) {
                        first = false;
                    } else {
                        b.append(", ");
                    }
                    render(b, args.remove(name));
                }
                if (!args.isEmpty()) {
                    // TODO handle somehow, maybe Groovy with {…}?
                    throw new UnsupportedOperationException("@DataBoundSetter not yet supported in value of type " + valueC + ": " + args.keySet());
                }
                b.append(')');
            } catch (NoStaplerConstructorException x) {
                b.append("<object of type ").append(valueC.getCanonicalName()).append('>');
            }
        }
    }

    private Snippetizer() {}

}
