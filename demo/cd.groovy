// TODO consider using https://github.com/cloudbees/jenkins-docker-executors to host everything (install graphviz on Jenkins node)
// Prep: mkdir /tmp/webapps && docker run -p 80:8080 -v /tmp/webapps:/opt/jetty/webapps jglick/jetty-demo &

steps.stage('Dev')
with.node('master') {
    def src = 'https://github.com/jenkinsci/workflow-plugin-pipeline-demo.git'
    steps.git(url: src)
    sh('mvn clean package')
    steps.archive('target/x.war')
    stage('QA')
    parallel(sometests: {
        runWithServer {url ->
            sh("mvn -f sometests/pom.xml test -Durl=${url}")
        }
    }, othertests: {
        runWithServer {url ->
            sh("mvn -f sometests/pom.xml test -Durl=${url}") // TODO add other test module
        }
    })
    steps.stage(value: 'Staging', concurrency: 1)
    deploy('target/x.war', 'staging')
}
steps.input(message: "Does http://localhost/staging/ look good?")
steps.checkpoint('Before production')
steps.stage(value: 'Production', concurrency: 1)
with.node('master') {
    sh('curl -I http://localhost/staging/')
    steps.unarchive(mapping: ['target/x.war' : 'x.war'])
    deploy('x.war', 'production')
    steps.echo 'Deployed to http://localhost/production/'
}

def deploy(war, id) {
    sh("cp ${war} /tmp/webapps/${id}.war")
}

def undeploy(id) {
    sh("rm /tmp/webapps/${id}.war")
}

def runWithServer(body) {
    def id = UUID.randomUUID().toString()
    deploy('target/x.war', id)
    try {
        body.call("http://localhost/${id}/");
    } finally {
        undeploy(id)
    }
}
