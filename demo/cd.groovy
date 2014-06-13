// TODO consider using https://github.com/cloudbees/jenkins-docker-executors to host everything (install graphviz on Jenkins node)
// Prep: mkdir /tmp/webapps && docker run -p 80:8080 -v /tmp/webapps:/opt/jetty/webapps jglick/jetty-demo &

@WorkflowMethod
def runWithServer(body) {
    def id = UUID.randomUUID().toString()
    sh("cp target/x.war /tmp/webapps/${id}.war")
    try {
        body.run("http://localhost/${id}/");
    } finally {
        sh("rm /tmp/webapps/${id}.war")
    }
}

steps.segment('Dev')
with.node(/*'heavy'*/) {
    with.ws() {
        def src = 'https://github.com/jenkinsci/workflow-plugin-pipeline-demo.git'
        // TODO pending SCM-Job merge steps.git(url: src)
        sh("if [ -d .git ]; then git pull; else git clone ${src} tmp && mv tmp/.git tmp/* . && rmdir tmp; fi")
        sh('mvn clean package')
        steps.archive('target/x.war')
        segment('QA')
        parallel(sometests: {
            runWithServer {url ->
                sh("mvn -f sometests test -Durl=${url}")
            }
        }, othertests: {
            runWithServer {port ->
                sh("mvn -f othertests test -Durl=${url}")
            }
        })
        steps.segment(value: 'Staging', concurrency: 1)
        sh('cp target/x.war /tmp/webapps/staging.war')
    }
}
steps.input(message: "Does http://localhost/staging/ look good?")
steps.checkpoint() // if have cps-checkpoint plugin installed, else comment out
steps.segment(value: 'Production', concurrency: 1)
with.node(/*'light'*/) {
    with.ws() {
        steps.unarchive(mapping: ['target/x.war' : 'x.war'])
        sh('cp target/x.war /tmp/webapps/production.war')
        steps.echo 'Deployed to http://localhost/production/'
    }
}
