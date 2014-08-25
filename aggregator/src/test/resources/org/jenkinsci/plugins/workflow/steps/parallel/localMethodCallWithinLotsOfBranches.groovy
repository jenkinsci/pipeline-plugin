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
        // TODO x["branch${i}"] = {…} does not work: https://trello.com/c/TF0aLhA9/15-groovy-cps-transformer-wave-4
        x.put("branch${i}", { notify("Hello ${j}") })
    }

    parallel(x)
}
