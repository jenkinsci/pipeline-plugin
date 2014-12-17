def notify(msg) {
    sh "echo ${msg}"
    sh "echo ${msg} 2"
    sh "echo ${msg} 3"
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
