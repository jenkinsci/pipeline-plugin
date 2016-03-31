def deploy(id) {
    unstash 'war'
    sh "cp x.war /tmp/webapps/${id}.war"
}

def undeploy(id) {
    sh "rm /tmp/webapps/${id}.war"
}

def runWithServer(body) {
    def id = UUID.randomUUID().toString()
    deploy id
    try {
        body.call id
    } finally {
        undeploy id
    }
}

this
