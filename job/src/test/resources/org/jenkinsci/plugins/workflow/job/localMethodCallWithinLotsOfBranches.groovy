def notify(msg) {
    sh "echo ${msg}"
}

with.node {
    with.ws {}
    sh 'echo start'
    def x = []
    for (def i=0; i<1024; i++) {
        def j = i;
        x.add({ notify("Hello ${j}") })
    }

    parallel(x)
    sh 'echo end'
}