// TODO consider using https://github.com/cloudbees/jenkins-docker-executors to host everything (install graphviz on Jenkins node)
// Prep: mkdir /tmp/webapps && docker run -p 80:8080 -v /tmp/webapps:/opt/jetty/webapps jglick/jetty-demo &

def runWithServer(body) {
    def id = UUID.randomUUID().toString()
    sh("cp target/x.war /tmp/webapps/${id}.war")
    try {
        body.call("http://localhost/${id}/");
    } finally {
        sh("rm /tmp/webapps/${id}.war")
    }
}

steps.segment('Dev')
with.node(/*'heavy'*/) {
    def src = 'https://github.com/jenkinsci/workflow-plugin-pipeline-demo.git'
    // TODO pending SCM-Job merge steps.git(url: src)
    sh("if [ -d .git ]; then git pull; else git clone ${src} tmp && mv tmp/.git tmp/* . && rmdir tmp; fi")
    sh('mvn clean package')
    steps.archive('target/x.war')
    segment('QA')
    parallel(sometests: {
        runWithServer {url ->
            sh("mvn -f sometests/pom.xml test -Durl=${url}")
        }
    }, othertests: {
        runWithServer {url ->
            sh("mvn -f sometests/pom.xml test -Durl=${url}") // TODO add other test module
        }
    })
    steps.segment(value: 'Staging', concurrency: 1)
    sh('cp target/x.war /tmp/webapps/staging.war')
}
steps.input(message: "Does http://localhost/staging/ look good?")
steps.checkpoint() // if have cps-checkpoint plugin installed, else comment out
steps.segment(value: 'Production', concurrency: 1)
with.node(/*'light'*/) {
    steps.unarchive(mapping: ['target/x.war' : 'x.war'])
    sh('cp target/x.war /tmp/webapps/production.war')
    steps.echo 'Deployed to http://localhost/production/'
}
