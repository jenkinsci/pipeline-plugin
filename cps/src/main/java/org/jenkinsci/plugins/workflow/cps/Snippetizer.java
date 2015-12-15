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

import hudson.Extension;
import hudson.Functions;
import hudson.model.RootAction;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.SourceVersion;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.structs.DescribableHelper;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.ClassDescriptor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Takes a {@link Step} as configured through the UI and tries to produce equivalent Groovy code.
 * Render using: {@code <st:include it="${theSnippetizerInstance}" page="block.jelly"/>}
 */
@Extension public class Snippetizer implements RootAction {
    
    static String object2Groovy(Object o) throws UnsupportedOperationException {
        Class<? extends Object> clazz = o.getClass();
        for (StepDescriptor d : StepDescriptor.all()) {
            if (d.clazz.equals(clazz)) {
                StringBuilder b = new StringBuilder(d.getFunctionName());
                Step step = (Step) o;
                Map<String,Object> args = new TreeMap<String,Object>(d.defineArguments(step));
                boolean first = true;
                boolean singleMap = args.size() == 1 && args.values().iterator().next() instanceof Map;
                for (Map.Entry<String,Object> entry : args.entrySet()) {
                    if (first) {
                        first = false;
                        if (d.takesImplicitBlockArgument() || singleMap) {
                            b.append('(');
                        } else {
                            b.append(' ');
                        }
                    } else {
                        b.append(", ");
                    }
                    String key = entry.getKey();
                    if (args.size() > 1 || !isDefaultKey(step, key)) {
                        b.append(key).append(": ");
                    }
                    render(b, entry.getValue());
                }
                if (d.takesImplicitBlockArgument()) {
                    if (!args.isEmpty()) {
                        b.append(')');
                    }
                    b.append(" {\n    // some block\n}");
                } else if (singleMap) {
                    b.append(')');
                } else if (args.isEmpty()) {
                    b.append("()");
                }
                return b.toString();
            }
        }
        throw new UnsupportedOperationException("Unknown step " + clazz);
    }

    private static boolean isDefaultKey(Step step, String key) {
        String[] names = new ClassDescriptor(step.getClass()).loadConstructorParamNames();
        return names.length == 1 && key.equals(names[0]);
    }

    private static void render(StringBuilder b, Object value) {
        if (value == null) {
            b.append("null");
            return;
        }
        Class<?> valueC = value.getClass();
        if (valueC == String.class || valueC == Character.class) {
            String text = String.valueOf(value);
            if (text.contains("\n")) {
                b.append("'''").append(text.replace("\\", "\\\\").replace("'", "\\'")).append("'''");
            } else {
                b.append('\'').append(text.replace("\\", "\\\\").replace("'", "\\'")).append('\'');
            }
        } else if (valueC == Boolean.class || valueC == Integer.class || valueC == Long.class) {
            b.append(value);
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
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
        } else if (value instanceof Map) {
            Map<?,?> map = (Map) value;
            b.append('[');
            boolean first = true;
            for (Map.Entry<?,?> entry : map.entrySet()) {
                if (first) {
                    first = false;
                } else {
                    b.append(", ");
                }
                Object key = entry.getKey();
                if (key instanceof String && SourceVersion.isName((String) key)) {
                    b.append(key);
                } else {
                    render(b, key);
                }
                b.append(": ");
                render(b, entry.getValue());
            }
            b.append(']');
        } else {
            b.append("<object of type ").append(valueC.getCanonicalName()).append('>');
        }
    }

    private static final String ACTION_URL = "workflow-cps-snippetizer";

    @Override public String getUrlName() {
        return ACTION_URL;
    }

    @Override public String getIconFileName() {
        return null;
    }

    @Override public String getDisplayName() {
        return null;
    }

    @Restricted(DoNotUse.class)
    public Collection<? extends StepDescriptor> getStepDescriptors(boolean advanced) {
        TreeSet<StepDescriptor> t = new TreeSet<StepDescriptor>(new StepDescriptorComparator());
        for (StepDescriptor d : StepDescriptor.all()) {
            if (d.isAdvanced() == advanced) {
                t.add(d);
            }
        }
        return t;
    }

    @Restricted(DoNotUse.class) // for stapler
    public Iterable<GlobalVariable> getGlobalVariables() {
        // TODO order TBD. Alphabetical? Extension.ordinal?
        return GlobalVariable.ALL;
    }

    @Restricted(NoExternalUse.class)
    public static final String HELP_URL = ACTION_URL + "/help";

    @Restricted(DoNotUse.class)
    public void doHelp(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rsp.serveLocalizedFile(req, Snippetizer.class.getResource("Snippetizer/help.html"));
    }

    @Restricted(NoExternalUse.class)
    public static final String GENERATE_URL = ACTION_URL + "/generateSnippet";

    @Restricted(DoNotUse.class) // accessed via REST API
    public HttpResponse doGenerateSnippet(StaplerRequest req, @QueryParameter String json) throws Exception {
        // TODO JENKINS-31458 is there not an easier way to do this?
        JSONObject jsonO = JSONObject.fromObject(json);
        Jenkins j = Jenkins.getActiveInstance();
        Class<?> c = j.getPluginManager().uberClassLoader.loadClass(jsonO.getString("stapler-class"));
        StepDescriptor descriptor = (StepDescriptor) j.getDescriptor(c.asSubclass(Step.class));
        Object o;
        try {
            o = descriptor.newInstance(req, jsonO);
        } catch (RuntimeException x) { // e.g. IllegalArgumentException
            return HttpResponses.plainText(Functions.printThrowable(x));
        }
        try {
            String groovy = object2Groovy(o);
            if (descriptor.isAdvanced()) {
                String warning = Messages.Snippetizer_this_step_should_not_normally_be_used_in();
                groovy = "// " + warning + "\n" + groovy;
            }
            return HttpResponses.plainText(groovy);
        } catch (UnsupportedOperationException x) {
            Logger.getLogger(CpsFlowExecution.class.getName()).log(Level.WARNING, "failed to render " + json, x);
            return HttpResponses.plainText(x.getMessage());
        }
    }

    @Restricted(NoExternalUse.class)
    public static final String STATIC_URL = ACTION_URL + "/static";

    @Restricted(DoNotUse.class)
    public void doStatic(StaplerRequest req, StaplerResponse rsp) throws Exception {
        rsp.setContentType("text/html;charset=UTF-8");
        PrintWriter pw = rsp.getWriter();
        pw.println("<html><head><title>Jenkins Workflow Reference</title></head><body>");
        pw.println("<h1>Steps</h1>");
        for (StepDescriptor d : getStepDescriptors(false)) {
            generateStepHelp(d, pw);
        }
        pw.println("<h1>Advanced/Deprecated Steps</h1>");
        for (StepDescriptor d : getStepDescriptors(true)) {
            generateStepHelp(d, pw);
        }
        pw.println("<h1>Variables</h1>");
        for (GlobalVariable v : getGlobalVariables()) {
            pw.println("<h2><code>" + v.getName() + "</code></h2>");
            RequestDispatcher rd = req.getView(v, "help");
            if (rd != null) {
                /* TODO does not work:
                rd.include(req, rsp);
                */
            } else {
                pw.println("(no help)");
            }
        }
        pw.println("</body></html>");
    }
    private static void generateStepHelp(StepDescriptor d, PrintWriter pw) throws Exception {
        pw.println("<h2><code>" + d.getFunctionName() + "</code>: " + d.getDisplayName() + "</h2>");
        try {
            generateHelp(DescribableHelper.schemaFor(d.clazz), pw, 3);
        } catch (Exception x) {
            pw.println("<pre><code>" + /*Util.escape(Functions.printThrowable(x))*/x + "</code></pre>");
        }
    }
    private static void generateHelp(DescribableHelper.Schema schema, PrintWriter pw, int headerLevel) throws Exception {
        String help = schema.getHelp(null);
        if (help != null) {
            pw.println(help);
        } // TODO else could use RequestDispatcher (as in Descriptor.doHelp) to serve template-based help
        for (String attr : schema.mandatoryParameters()) {
            pw.println("<h" + headerLevel + "><code>" + attr + "</code></h" + headerLevel + ">");
            generateAttrHelp(schema, attr, pw, headerLevel);
        }
        for (String attr : schema.parameters().keySet()) {
            if (schema.mandatoryParameters().contains(attr)) {
                continue;
            }
            pw.println("<h" + headerLevel + "><code>" + attr + "</code> (optional)</h" + headerLevel + ">");
            generateAttrHelp(schema, attr, pw, headerLevel);
        }
    }
    private static void generateAttrHelp(DescribableHelper.Schema schema, String attr, PrintWriter pw, int headerLevel) throws Exception {
        String help = schema.getHelp(attr);
        if (help != null) {
            pw.println(help);
        }
        DescribableHelper.ParameterType type = schema.parameters().get(attr);
        describeType(type, pw, headerLevel);
    }
    private static void describeType(DescribableHelper.ParameterType type, PrintWriter pw, int headerLevel) throws Exception {
        int nextHeaderLevel = Math.min(6, headerLevel + 1);
        if (type instanceof DescribableHelper.AtomicType) {
            pw.println("<p><strong>Type:</strong>" + type + "</p>");
        } else if (type instanceof DescribableHelper.EnumType) {
            pw.println("<p><strong>Values:</strong></p><ul>");
            for (String v : ((DescribableHelper.EnumType) type).getValues()) {
                pw.println("<li><code>" + v + "</code></li>");
            }
            pw.println("</ul>");
        } else if (type instanceof DescribableHelper.ArrayType) {
            pw.println("<p><strong>Array/List</strong></p>");
            describeType(((DescribableHelper.ArrayType) type).getElementType(), pw, headerLevel);
        } else if (type instanceof DescribableHelper.HomogeneousObjectType) {
            pw.println("<p><strong>Nested object</strong></p>");
            generateHelp(((DescribableHelper.HomogeneousObjectType) type).getType(), pw, nextHeaderLevel);
        } else if (type instanceof DescribableHelper.HeterogeneousObjectType) {
            pw.println("<p><strong>Nested choice of objects</strong></p><ul>");
            for (Map.Entry<String,DescribableHelper.Schema> entry : ((DescribableHelper.HeterogeneousObjectType) type).getTypes().entrySet()) {
                pw.println("<li><code>$class: '" + entry.getKey() + "'</code></li>");
                generateHelp(entry.getValue(), pw, nextHeaderLevel);
            }
            pw.println("</ul>");
        } else if (type instanceof DescribableHelper.ErrorType) {
            Exception x = ((DescribableHelper.ErrorType) type).getError();
            pw.println("<pre><code>" + /*Util.escape(Functions.printThrowable(x))*/x + "</code></pre>");
        } else {
            assert false : type;
        }
    }

    private static class StepDescriptorComparator implements Comparator<StepDescriptor>, Serializable {
        @Override
        public int compare(StepDescriptor o1, StepDescriptor o2) {
            return o1.getFunctionName().compareTo(o2.getFunctionName());
        }
        private static final long serialVersionUID = 1L;
    }

}
