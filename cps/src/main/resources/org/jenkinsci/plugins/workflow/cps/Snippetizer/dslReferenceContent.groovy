package org.jenkinsci.plugins.workflow.cps.Snippetizer

import org.jenkinsci.plugins.workflow.cps.GlobalVariable
import org.jenkinsci.plugins.workflow.cps.Snippetizer
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.structs.DescribableHelper

import javax.servlet.RequestDispatcher

Snippetizer snippetizer = my;

def l = namespace(lib.LayoutTagLib)
def st = namespace("jelly:stapler")


h1(_("Steps"))
for (StepDescriptor d : snippetizer.getStepDescriptors(false)) {
    generateStepHelp(d);
}

h1(_("Advanced/Deprecated Steps"))
for (StepDescriptor d : snippetizer.getStepDescriptors(true)) {
    generateStepHelp(d);
}

h1(_("Variables"))

for (GlobalVariable v : snippetizer.getGlobalVariables()) {
    h2 {
        code(v.getName())
    }
    RequestDispatcher rd = request.getView(v, "help");
    div(class:"help", style:"display: block") {
        if (rd != null) {
            st.include(page: "help", it: v)
        } else {
            raw("(no help)")
        }
    }
}


def generateStepHelp(StepDescriptor d) throws Exception {
    return {
        h2 {
            code(d.getFunctionName())
            raw(": ${d.getDisplayName()}")
        }
        try {
            generateHelp(DescribableHelper.schemaFor(d.clazz), 3);
        } catch (Exception x) {
            pre {
                code(x)
            }
        }
    }.call()
}

def generateHelp(DescribableHelper.Schema schema, int headerLevel) throws Exception {
    return {
        String help = schema.getHelp(null);
        if (help != null && !help.equals("")) {
            div(class:"help", style:"display: block") {
                raw(help)
            }
        } // TODO else could use RequestDispatcher (as in Descriptor.doHelp) to serve template-based help
        for (String attr : schema.mandatoryParameters()) {
            "h${headerLevel}" {
                code(attr)
            }
            generateAttrHelp(schema, attr, headerLevel);
        }
        for (String attr : schema.parameters().keySet()) {
            if (schema.mandatoryParameters().contains(attr)) {
                continue;
            }
            "h${headerLevel}" {
                code(attr)
                raw(" (optional)")
            }
            generateAttrHelp(schema, attr, headerLevel);
        }
    }.call()
}

def generateAttrHelp(DescribableHelper.Schema schema, String attr, int headerLevel) throws Exception {
    return {
        String help = schema.getHelp(attr);
        if (help != null && !help.equals("")) {
            div(class:"help", style:"display: block") {
                raw(help)
            }
        }
        DescribableHelper.ParameterType type = schema.parameters().get(attr);
        describeType(type, headerLevel);
    }.call()
}

def describeType(DescribableHelper.ParameterType type, int headerLevel) throws Exception {
    return {
        int nextHeaderLevel = Math.min(6, headerLevel + 1);
        if (type instanceof DescribableHelper.AtomicType) {
            p {
                strong(_("Type:"))
                text(type)
            }
        } else if (type instanceof DescribableHelper.EnumType) {
            p {
                strong(_("Values:"))
            }

            ul {
                for (String v : ((DescribableHelper.EnumType) type).getValues()) {
                    li {
                        code(v)
                    }
                }
            }
        } else if (type instanceof DescribableHelper.ArrayType) {
            p {
                strong(_("Array/List"))
            }
            describeType(((DescribableHelper.ArrayType) type).getElementType(), headerLevel)
        } else if (type instanceof DescribableHelper.HomogeneousObjectType) {
            p {
                strong(_("Nested object"))
            }
            generateHelp(((DescribableHelper.HomogeneousObjectType) type).getType(), nextHeaderLevel);
        } else if (type instanceof DescribableHelper.HeterogeneousObjectType) {
            p {
                strong(_("Nested choice of objects"))
            }

            ul {
                for (Map.Entry<String, DescribableHelper.Schema> entry : ((DescribableHelper.HeterogeneousObjectType) type).getTypes().entrySet()) {
                    li {
                        code(DescribableHelper.CLAZZ + ": '" + entry.getKey() + "'")
                    }
                    generateHelp(entry.getValue(), nextHeaderLevel);
                }
            }
        } else if (type instanceof DescribableHelper.ErrorType) {
            Exception x = ((DescribableHelper.ErrorType) type).getError();
            pre {
                code(x)
            }
        } else {
            assert false: type;
        }
    }.call()
}