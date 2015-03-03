package org.jenkinsci.plugins.workflow.support.steps.build.BuildTriggerStep;
def st = namespace('jelly:stapler')
def l = namespace('/lib/layout')
l.ajax {
    def jobName = request.getParameter('job')
    if (jobName != null) {
        def job = app.getItemByFullName(jobName, hudson.model.Job)
        if (job != null) {
            def pdp = job.getProperty(hudson.model.ParametersDefinitionProperty)
            if (pdp != null) {
                table(width: '100%', class: 'parameters') {
                    for (parameterDefinition in pdp.parameterDefinitions) {
                        tbody {
                            st.include(it: parameterDefinition, page: parameterDefinition.descriptor.valuePage)
                        }
                    }
                }
            } else {
                text("${job.fullDisplayName} is not parameterized")
            }
        } else {
            text("no such job ${jobName}")
        }
    } else {
        text('no job specified')
    }
}
