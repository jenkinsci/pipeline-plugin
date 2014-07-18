Docker image for workflow demo
==============================

This container includes Jenkins with workflow plugin and Jetty to demonstrate a continuous delivery pipeline of Java web application.
It highlights key parts of the workflow plugin:

Run it like:

    docker run -p 8080:8080 -p 8081:8081 -p 8022:22 -ti jenkinsci/workflow-demo

Jenkins runs on port 8081, and Jetty runs on port 8080. The continuous delivery pipeline consists of the following sequence.

* Check out source code from a Git repository and build it via Maven with unit testing
* Run two parallel integration tests that involve deploying the app to a PaaS-like ephemeral server instances, which get
  thrown away when tests are done (this is done by using auto-deployment of Jetty)
* Once integration tests are successful, the webapp gets to the staging server at http://localhost:8080/staging/
* Human will manually inspect the staging instance, and when ready, approves the deployment to the production server at http://localhost:8080/production/
* Workflow completes

You can login as root in the demo container via `ssh -p 8022 root@localhost`. The password is `root`.
(If you have `nsenter`, [you can use nsenter instead of ssh for a smoother demo](http://jpetazzo.github.io/2014/06/23/docker-ssh-considered-evil/)
This is useful to kill Jetty to simulate a failure in the production deployment (via `pkill -9 -f jetty`) or restart it (via `jetty &`)

If you are running this demo behind a corporate proxy, then please see [Using a http proxy](docs/USING-A-HTTP-PROXY.md)

Sample demo scenario
--------------------

* Explain the setup of the continuous delivery pipeline in a picture
* Go to [job configuration](http://localhost:8081/job/cd/configure) and walk through the workflow script
  and how that implements the pipeline explained above
    * Discuss use of abstractions like functions to organize complex workflow
    * Highlight and explain some of the primitives, such as `stage`, `input`, and `with.node`
* Get one build going, and watch [Jetty top page](http://localhost:8080/) to see ephemeral test instances
  deployed and deleted
* When it gets to the pause, go to the pause UI in the build top page and terminate the workflow
* Get another build going, but this time restart the Jenkins instance while the workflow is in progress
  via [restart UI](http://localhost:8081/restart). Doing this while the integration test is running,
  as steps like Git checkout will get disrupted by restart.
