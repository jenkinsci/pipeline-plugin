def notify(msg) {
    doNotify msg
    doNotify "${msg} 2"
    doNotify "${msg} 3"
}
def doNotify(msg) {
    if (hudson.Functions.windows) {
        bat "echo ${msg}"
    } else {
        sh "echo ${msg}"
    }
}

node {
    ws {}
    def x = [:]
    for (def i=0; i<128; i++) {
        def j = i;
        // TODO JENKINS-25979 x["branch${i}"] = {…} does not work
        x.put("branch${i}", { notify("Hello ${j}") })
    }

    parallel(x)
}
