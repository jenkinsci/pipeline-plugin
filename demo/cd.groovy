def runWithServer(body) {
    sh('./create-server target/x.war');
    def id = new File(pwd(), 'id').text
    def port = new File(pwd(), 'port').text
    try {
        body.run(port);
    } finally {
        sh("./destroy-server ${id}")
    }
}

segment('Dev')
with.node('heavy') {
    with.ws() {
        steps.git(url: '…')
        sh('mvn clean package')
        archive('target/x.war')
        segment(value: 'QA')
        parallel({
            runWithServer {port ->
                sh("mvn -f sometests test -Dport=$port")
            }
        }, {
            runWithServer {port ->
                sh("mvn -f othertests test -Dport=$port")
            }
        })
        segment(value: 'Staging', concurrency: 1)
        sh('./deploy staging target/x.war')
    }
}
input(message: "Does http://localhost/staging/ look good?")
checkpoint() // TODO this must copy artifacts from original to this
segment(value: 'Prod', concurrency: 1)
with.node('light') {
    with.ws() {
        unarchive(['target/x.war' : 'x.war'])
        sh('./deploy prod x.war')
    }
}
