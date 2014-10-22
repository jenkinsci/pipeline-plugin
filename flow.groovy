def devQAStaging() {
    env.PATH="${tool 'Maven 3.x'}/bin:${env.PATH}"
    stage 'Dev'
    sh 'mvn -o clean package'
    archive 'target/x.war'

    stage 'QA'

    parallel(longerTests: {
        runWithServer {url ->
            sh "mvn -o -f sometests/pom.xml test -Durl=${url} -Dduration=30"
        }
    }, quickerTests: {
        runWithServer {url ->
            sh "mvn -o -f sometests/pom.xml test -Durl=${url} -Dduration=20"
        }
    })
    stage name: 'Staging', concurrency: 1
    deploy 'target/x.war', 'staging'
}

def production() {
    input message: "Does http://localhost:8080/staging/ look good?"
    try {
        checkpoint('Before production')
    } catch (NoSuchMethodError _) {
        echo 'Checkpoint feature available in Jenkins Enterprise by CloudBees.'
    }
    stage name: 'Production', concurrency: 1
    node('master') {
        sh 'curl -I http://localhost:8080/staging/'
        unarchive mapping: ['target/x.war' : 'x.war']
        deploy 'x.war', 'production'
        echo 'Deployed to http://localhost:8080/production/'
    }
}

def deploy(war, id) {
    sh "cp ${war} /tmp/webapps/${id}.war"
}

def undeploy(id) {
    sh "rm /tmp/webapps/${id}.war"
}

def runWithServer(body) {
    def id = UUID.randomUUID().toString()
    deploy 'target/x.war', id
    try {
        body.call "http://localhost:8080/${id}/"
    } finally {
        undeploy id
    }
}

return this;
