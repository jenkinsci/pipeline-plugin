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

import java.util.Set;
import java.util.TreeSet;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

/**
 * Takes a {@link Step} as configured through the UI and tries to produce equivalent Groovy code.
 */
class Snippetizer {
    
    private static final String CLAZZ = "stapler-class";

    public static String json2Groovy(String json) {
        JSONObject o = JSONObject.fromObject(json);
        String clazz = o.getString(CLAZZ);
        String func = null;
        for (StepDescriptor d : StepDescriptor.all()) {
            if (d.getId().equals(clazz)) {
                func = d.getFunctionName();
                break;
            }
        }
        if (func == null) {
            return "Unknown step " + clazz;
        }
        StringBuilder b = new StringBuilder(func);
        @SuppressWarnings("unchecked")
        Set<String> keys = new TreeSet<String>(o.keySet());
        keys.remove(CLAZZ);
        for (String key : keys) {
            if (key.equals("value") && keys.size() == 1) {
                b.append(" '").append(o.getString(key)).append('\'');
            } else {
                b.append(' ').append(key).append(": '").append(o.getString(key)).append('\'');
            }
        }
        return b.toString();
    }

    private Snippetizer() {}

}
