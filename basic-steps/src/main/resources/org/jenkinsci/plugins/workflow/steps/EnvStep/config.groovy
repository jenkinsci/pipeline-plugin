package org.jenkinsci.plugins.workflow.steps.EnvStep
f = namespace(lib.FormTagLib)
f.entry(field: 'overrides', title: 'Environment variable overrides') {
    f.textarea(value: instance == null ? '' : instance.overrides.join('\n'))
}
