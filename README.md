# Introduction

Building continuous delivery pipelines and similarly complex tasks in Jenkins using freestyle projects and traditional plugins can be awkward.
You need to mix Parameterized Trigger, Copy Artifact, Promoted Builds, Conditional Build Step, and more just to express what should be a simple script.
The Pipeline plugin (formerly known as Workflow) suite attempts to make it possible to directly write that script, what people often call a _pipeline_, while integrating with Jenkins features like slaves and publishers.

# Features

## Scripted control flow

Your whole pipeline is a single Groovy script using an embedded DSL, possibly quite short and legible; there is no need to jump between multiple job configuration screens to see what is going on.
Conditions, loops, variables, parallel tasks, and so on are defined using regular language constructs.
At any point you can insert a shell/batch script to do “real work” (compilation, etc.).

## Useful steps

Standard DSL functions (“steps”) let you run external processes, grab slave nodes and workspaces, perform SCM checkouts, build other projects (pipeline or freestyle), wait for external conditions, and so on.
Plugins can add further steps.

## Pause and resume execution

If Jenkins is restarted (intentionally, or because of a crash) while your Pipeline is running, when it comes back up, execution is resumed where it left off.
This applies to external processes (shell scripts) so long as the slave can be reattached, and losing the slave connection temporarily is not fatal either.

Pipelines can pause in the middle and wait for a human to approve something, or enter some information.
Executors need not be consumed while the Pipeline is waiting.

## Pipeline stages

Pipelines can be divided into sequential stages, not only for labeling but to throttle concurrency.

# Getting started

Read the [tutorial](TUTORIAL.md) to get started writing pipelines.

There is also a [DZone Refcard](https://dzone.com/refcardz/continuous-delivery-with-jenkins-workflow).

A new collection of [examples, snippets, tips, and tricks](https://github.com/jenkinsci/pipeline-examples) is in progress.

# Installation

Releases are available on the Jenkins update center.
You need to be running a sufficiently recent Jenkins release: an LTS in the 1.580.x line or newer (currently 1.609.x for the latest updates), or a weekly release.

For OSS Jenkins users, install _Pipeline_ (its dependencies will be pulled in automatically).
You will need to restart Jenkins to complete installation.

CloudBees Jenkins Enterprise users get Pipeline automatically as of the 14.11 (1.580.1.1) release.
Otherwise install _CloudBees Pipeline_ from the update center.
Again dependencies will be pulled in automatically, including all the OSS plugins.

For multibranch pipelines and organization folders, install _Pipeline: Multibranch_ (includes as of _Pipeline_ 2.0) plus at least one SCM provider, such as _GitHub Branch Source_.

# News & questions

* [jenkins-workflow tag](http://stackoverflow.com/tags/jenkins-workflow) on StackOverflow
* [JIRA](https://issues.jenkins-ci.org/issues/?jql=project%20%3D%20JENKINS%20AND%20resolution%20%3D%20Unresolved%20AND%20%28component%20in%20%28pipeline%2C%20pipeline-build-step-plugin%2C%20pipeline-graph-analysis-plugin%2C%20pipeline-input-step-plugin%2C%20pipeline-milestone-step-plugin%2C%20pipeline-stage-step-plugin%2C%20pipeline-stage-view-plugin%2C%20workflow-aggregator-plugin%2C%20workflow-api-plugin%2C%20workflow-basic-steps-plugin%2C%20workflow-cps-global-lib-plugin%2C%20workflow-cps-plugin%2C%20workflow-durable-task-step-plugin%2C%20workflow-job-plugin%2C%20workflow-multibranch-plugin%2C%20workflow-scm-step-plugin%2C%20workflow-step-api-plugin%2C%20workflow-support-plugin%29%20OR%20labels%20in%20%28pipeline%29%29%20ORDER%20BY%20component%20ASC%2C%20key%20DESC&mode=hide) (file issues in the `pipeline` component, or other components with the `pipeline` label)
* [User list discussions](https://groups.google.com/forum/#!topicsearchin/jenkinsci-users/pipeline) (mention `pipeline` in the subject)
* [#JenkinsPipeline](https://twitter.com/hashtag/JenkinsPipeline) on Twitter

# Demo

See the [demo overview](https://github.com/jenkinsci/workflow-aggregator-plugin/blob/master/demo/README.md) using Docker if you want to try a complete setup quickly. In short:

    docker run -p 8080:8080 -p 8081:8081 -p 8022:22 -ti jenkinsci/workflow-demo

and browse [localhost:8080](http://localhost:8080/).

# Presentations

Webinar _Continuous Delivery as Code with Jenkins Workflow_ (Sep 2015): [slides](https://www.cloudbees.com/sites/default/files/webinar-_continuous_delivery_as_code_with_jenkins_workflow.pdf) and [video](https://youtu.be/Q2pZdzaaCXg) (demo starts at 20:30)

Jenkins Workflow: What’s Up? (JUC West) (Sep 2015): [slides](http://www.slideshare.net/jgcloudbees/juc-west-15-jenkins-workflow-whats-up) and [video](https://youtu.be/VkIzoU7zYzE)

Jenkins Office Hour on Workflow for plugin developers (Aug 2015): [video](https://www.youtube.com/watch?v=4zdy7XGx3PA)

Workflow Meetup London (Mar 2015): [slides](http://www.slideshare.net/jgcloudbees/london-workflow-summit-kkjg)

Jenkins Workflow Screencast (Jan 2015): [video](https://www.youtube.com/watch?v=Welwf1wTU-w)

Webinar _Orchestrating the Continuous Delivery Process in Jenkins with Workflow_ (Dec 2014): [video](http://youtu.be/ZqfiW8eVcuQ)

# Development

See the [contribution guide](CONTRIBUTING.md).
