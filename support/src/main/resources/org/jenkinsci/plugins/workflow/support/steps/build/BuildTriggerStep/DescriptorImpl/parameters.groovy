package org.jenkinsci.plugins.workflow.support.steps.build.BuildTriggerStep;
def st = namespace('jelly:stapler')
def l = namespace('/lib/layout')
l.ajax {
    def jobName = request.getParameter('job')
    if (jobName != null) {
        // Cf. BuildTriggerStepExecution:
        def contextName = request.getParameter('context')
        def context = contextName != null ? app.getItemByFullName(contextName) : null
        def job = app.getItem(jobName, (hudson.model.Item) context, jenkins.model.ParameterizedJobMixIn.ParameterizedJob)
        if (job != null) {
            def pdp = job.getProperty(hudson.model.ParametersDefinitionProperty)
            if (pdp != null) {
                // Cf. ParametersDefinitionProperty/index.jelly:
                table(width: '100%', class: 'parameters') {
                    for (parameterDefinition in pdp.parameterDefinitions) {
                        tbody {
                            // TODO JENKINS-26578 does not work for CredentialsParameterDefinition: pulldown is not populated because select.js is never loaded; <script> section in https://github.com/jenkinsci/credentials-plugin/commit/1045207207fb69d4dc1ede70d7ab743ad463708c not executed
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
