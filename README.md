# Introduction

Building continuous delivery pipelines and similarly complex tasks in Jenkins using freestyle projects and traditional plugins can be awkward.
You need to mix Parameterized Trigger, Copy Artifact, Promoted Builds, Conditional Build Step, and more just to express what should be a simple script.
The Pipeline plugin (formerly known as Workflow) suite attempts to make it possible to directly write that script, what people often call a _pipeline_, while integrating with Jenkins features like agents and publishers.

# Documentation

See the [official documentation on jenkins.io](https://jenkins.io/doc/book/pipeline/).

# News & questions

* [jenkins-pipeline tag](http://stackoverflow.com/tags/jenkins-pipeline) on StackOverflow
* [JIRA](https://issues.jenkins-ci.org/issues/?jql=project%20%3D%20JENKINS%20AND%20resolution%20%3D%20Unresolved%20AND%20%28component%20in%20%28pipeline%2C%20pipeline-build-step-plugin%2C%20pipeline-graph-analysis-plugin%2C%20pipeline-input-step-plugin%2C%20pipeline-milestone-step-plugin%2C%20pipeline-stage-step-plugin%2C%20pipeline-stage-view-plugin%2C%20workflow-aggregator-plugin%2C%20workflow-api-plugin%2C%20workflow-basic-steps-plugin%2C%20workflow-cps-global-lib-plugin%2C%20workflow-cps-plugin%2C%20workflow-durable-task-step-plugin%2C%20workflow-job-plugin%2C%20workflow-multibranch-plugin%2C%20workflow-scm-step-plugin%2C%20workflow-step-api-plugin%2C%20workflow-support-plugin%29%20OR%20labels%20in%20%28pipeline%29%29%20ORDER%20BY%20component%20ASC%2C%20key%20DESC&mode=hide) (file issues in the `pipeline` component, or other components with the `pipeline` label)
* [User list discussions](https://groups.google.com/forum/#!topicsearchin/jenkinsci-users/pipeline) (mention `pipeline` in the subject)
* [#JenkinsPipeline](https://twitter.com/hashtag/JenkinsPipeline) on Twitter

# Demo

See the [demo overview](https://github.com/jenkinsci/workflow-aggregator-plugin/blob/master/demo/README.md) using Docker if you want to try a complete setup quickly.

# Development

See the [contribution guide](CONTRIBUTING.md).
