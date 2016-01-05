package org.jenkinsci.plugins.workflow.cps.Snippetizer

import hudson.FilePath
import hudson.Functions
import org.jenkinsci.plugins.workflow.cps.GlobalVariable
import org.jenkinsci.plugins.workflow.cps.Snippetizer
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.structs.DescribableHelper
import org.jenkinsci.plugins.workflow.structs.DescribableHelper.Schema

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

Snippetizer snippetizer = my;

Map<StepDescriptor,Schema> steps = new LinkedHashMap<StepDescriptor, Schema>();

def errorSteps = [:]

[false, true].each { isAdvanced ->
    snippetizer.getStepDescriptors(isAdvanced).each { d ->
        Schema schema
        try {
            schema = DescribableHelper.schemaFor(d.clazz)
        } catch (Exception e) {
            println "Error on ${d.clazz}: ${Functions.printThrowable(e)}"
            errorSteps.put(d.clazz, e.message)
        }
        if (schema != null) {
            steps.put(d, schema)
        }

    }
}

def nodeContext = []
def scriptContext = []

steps.each { StepDescriptor step, Schema schema ->
    def params = schema.mandatoryParameters().collectEntries { p ->
        [p, objectTypeToMap(fetchActualClass(schema.parameters().get(p).actualType))]
    }
    def opts = schema.parameters().keySet().findAll { !(it in schema.mandatoryParameters()) }.collectEntries { k ->
        [k, objectTypeToMap(fetchActualClass(schema.parameters().get(k).actualType))]
    }

    boolean requiresNode = step.requiredContext.contains(FilePath)
    boolean takesClosure = step.takesImplicitBlockArgument()
    String description = step.displayName
    if (step.isAdvanced()) {
        description = "Advanced/Deprecated " + description
    }

    if (params.size() <= 1) {
        def fixedParams = params

        if (takesClosure) {
            fixedParams = params + ['body': "Closure"]
        }
        String contr = "method(name: '${step.functionName}', type: 'Object', useNamedArgs: false, params: ${fixedParams}, doc: '${description}')"
        if (requiresNode) {
            nodeContext.add(contr)
        } else {
            scriptContext.add(contr)
        }
    }
    if (!opts.isEmpty() || params.size() > 1) {
        def paramsMap = [:]
        if (takesClosure) {
            paramsMap.put('body', 'Closure')
        }

        for (def p : params) {
            paramsMap.put(p.key, p.value)
        }
        for (def p : opts) {
            paramsMap.put(p.key, p.value)
        }
        String contr = "method(name: '${step.functionName}', type: 'Object', useNamedArgs: true, params: ${paramsMap}, doc: '${step.displayName}')"

        if (requiresNode) {
            nodeContext.add(contr)
        } else {
            scriptContext.add(contr)
        }
    }
}

def globalVars = []

for (GlobalVariable v : snippetizer.getGlobalVariables()) {
    globalVars.add("property(name: '${v.getName()}', type: '${v.class}')")
}

def st = namespace("jelly:stapler")
st.contentType(value: "text/plain;charset=UTF-8")

raw("""
def insideNode = { enclosingCallName("node") & inClosure() }

(!insideNode() && currentType(subType(Script))).accept {
${scriptContext.join('\n')}
${globalVars.join('\n')}
}

//Steps that require a node context
insideNode().accept {
${nodeContext.join('\n')}
}

// Errors on:
${errorSteps.collect { k, v ->
    "// ${k}: ${v}"
}.join("\n")}
""")

// Render anything other than a primitive, Closure, or a java.* class as a Map.
def objectTypeToMap(String type) {
    if (type != null && type.contains(".") && !(type.startsWith("java"))) {
        return "Map"
    } else {
        return type
    }
}

def fetchActualClass(Type type) {
    if (type == null) {
        return "null"
    } else if (type instanceof Class) {
        return ((Class<?>) type).name
    } else if (type instanceof ParameterizedType) {
        return ((ParameterizedType) type).getRawType().toString()
    } else {
        return type.toString()
    }
}