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

import java.util.Map;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

/**
 * Takes a {@link Step} as configured through the UI and tries to produce equivalent Groovy code.
 */
class Snippetizer {
    
    public static String object2Groovy(Object o) throws UnsupportedOperationException {
        Class<? extends Object> clazz = o.getClass();
        for (StepDescriptor d : StepDescriptor.all()) {
            if (d.clazz.equals(clazz)) {
                StringBuilder b = new StringBuilder(d.getFunctionName());
                Map<String,Object> args = d.defineArguments((Step) o);
                for (Map.Entry<String,Object> entry : args.entrySet()) {
                    String key = entry.getKey();
                    if (key.equals("value") && args.size() == 1) {
                        b.append(" '").append(entry.getValue()).append('\'');
                    } else {
                        b.append(' ').append(key).append(": '").append(entry.getValue()).append('\'');
                    }
                }
                return b.toString();
            }
        }
        throw new UnsupportedOperationException("Unknown step " + clazz);
    }

    private Snippetizer() {}

}
