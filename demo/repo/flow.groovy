def jettyUrl = 'http://localhost:8081/'

node('slave') {
    git url: '/tmp/files/repo'
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

input message: "Does ${jettyUrl}staging/ look good?"
try {
    checkpoint('Before production')
} catch (NoSuchMethodError _) {
    echo 'Checkpoint feature available in Jenkins Enterprise by CloudBees.'
}
stage name: 'Production', concurrency: 1
node('master') {
    sh "wget -O - -S ${jettyUrl}staging/"
    unarchive mapping: ['target/x.war' : 'x.war']
    deploy 'x.war', 'production'
    echo "Deployed to ${jettyUrl}production/"
}

def deploy(war, id) {
    sh "cp ${war} /tmp/webapps/${id}.war"
}

def undeploy(id) {
    sh "rm /tmp/webapps/${id}.war"
}

def runWithServer(body) {
    def jettyUrl = 'http://localhost:8081/' // TODO why is this not inherited from the top-level scope?
    def id = UUID.randomUUID().toString()
    deploy 'target/x.war', id
    try {
        body.call "${jettyUrl}${id}/"
    } finally {
        undeploy id
    }
}
