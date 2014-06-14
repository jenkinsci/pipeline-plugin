def notify(msg) {
    sh "echo ${msg}"
    sh "echo ${msg} 2"
    sh "echo ${msg} 3"
}

with.node {
    with.ws {}
    def x = []
    for (def i=0; i<128; i++) {
        def j = i;
        x.add({ notify("Hello ${j}") })
    }

    parallel(x)
}