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
        // TODO you would expect x["branch${i}"] = {…} to work, but it does not.
        // For one thing, the CPS transformer apparently does not handle the …[…]=… operator yet.
        // For another, a GString in the key part of the map is not converted to String by DSL, breaking the StepDescriptor contract.
        x.put('branch' + i, { notify("Hello ${j}") })
    }

    parallel(x)
}
