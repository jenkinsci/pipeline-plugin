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
        def fixedParams = params.collectEntries { k, v ->
            [k, "'${v}'"]
        }
        if (takesClosure) {
            fixedParams = params + ['body': "'Closure'"]
        }
        String contr = "method(name: '${step.functionName}', type: 'Object', params: ${fixedParams}, doc: '${description}')"
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
        StringBuilder namedParamsS = new StringBuilder()
        for (def p : params) {
            namedParamsS.append("parameter(name: '${p.key}', type: '${p.value}'), ")
        }
        for (def p : opts) {
            namedParamsS.append("parameter(name: '${p.key}', type: '${p.value}'), ")
        }
        String contr
        if (takesClosure) {
            contr = "method(name: '${step.functionName}', type: 'Object', params: [body:Closure], namedParams: [${namedParamsS.toString()}], doc: '${step.displayName}')"
        } else {
            contr = "method(name: '${step.functionName}', type: 'Object', namedParams: [${namedParamsS.toString()}], doc: '${step.displayName}')"
        }
        if (requiresNode) {
            nodeContext.add(contr)
        } else {
            scriptContext.add(contr)
        }
    }
}

def globalVars = []

for (GlobalVariable v : snippetizer.getGlobalVariables()) {
    globalVars.add("property(name: '${v.getName()}', type: '${v.class.canonicalName}')")
}

def st = namespace("jelly:stapler")
st.contentType(value: "text/plain;charset=UTF-8")

raw("""
//The global script scope
def ctx = context(scope: scriptScope())
contributor(ctx) {
${scriptContext.join('\n')}
${globalVars.join('\n')}
}
//Steps that require a node context
def nodeCtx = context(scope: closureScope())
contributor(nodeCtx) {
    def call = enclosingCall('node')
    if (call) {
${nodeContext.join('\n')}
    }
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