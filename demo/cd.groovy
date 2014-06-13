// Prep: mkdir /tmp/webapps && docker run -p 80:8080 -v /tmp/webapps:/opt/jetty/webapps jglick/jetty-demo &

def runWithServer(body) {
    def id = UUID.randomUUID().toString()
    sh("cp target/x.war /tmp/webapps/${id}.war")
    try {
        body.run("http://localhost/${id}/");
    } finally {
        sh("rm /tmp/webapps/${id}.war")
    }
}

segment('Dev')
with.node(/*'heavy'*/) {
    with.ws() {
        def src = 'https://github.com/jenkinsci/workflow-plugin-pipeline-demo.git'
        //steps.git(url: src)
        sh("if [ -d .git ]; then git pull; else git clone ${src} .; fi")
        sh('mvn clean package')
        steps.archive('target/x.war')
        segment('QA')
        parallel({
            runWithServer {url ->
                sh("mvn -f sometests test -Durl=${url}")
            }
        }, {
            runWithServer {port ->
                sh("mvn -f othertests test -Durl=${url}")
            }
        })
        segment(value: 'Staging', concurrency: 1)
        sh('cp target/x.war /tmp/webapps/staging.war')
    }
}
input(message: "Does http://localhost/staging/ look good?")
checkpoint() // TODO this must copy artifacts from original to this
segment(value: 'Production', concurrency: 1)
with.node(/*'light'*/) {
    with.ws() {
        unarchive(mapping: ['target/x.war' : 'x.war'])
        sh('cp target/x.war /tmp/webapps/production.war')
        echo 'Deployed to http://localhost/production/'
    }
}
