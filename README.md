# Introduction

Building continuous delivery pipelines and similarly complex tasks in Jenkins using freestyle projects and traditional plugins can be awkward.
You need to mix Parameterized Trigger, Copy Artifact, Promoted Builds, Conditional Build Step, and more just to express what should be a simple script.
The Workflow plugin suite attempts to make it possible to directly write that script, what people often call a _workflow_ (sometimes abbreviated _flow_), while integrating with Jenkins features like slaves and publishers.

# Features

## Scripted control flow

Your whole workflow is a single Groovy script using an embedded DSL, possibly quite short and legible; there is no need to jump between multiple job configuration screens to see what is going on.
Conditions, loops, variables, parallel tasks, and so on are defined using regular language constructs.
At any point you can insert a shell/batch script to do “real work” (compilation, etc.).

## Useful steps

Standard DSL functions (“steps”) let you run external processes, grab slave nodes and workspaces, perform SCM checkouts, build other projects (workflow or freestyle), wait for external conditions, and so on.
Plugins can add further steps.

## Pause and resume execution

If Jenkins is restarted (intentionally, or because of a crash) while your workflow is running, when it comes back up, execution is resumed where it left off.
This applies to external processes (shell scripts) so long as the slave can be reattached, and losing the slave connection temporarily is not fatal either.

Flows can pause in the middle and wait for a human to approve something, or enter some information.
Executors need not be consumed while the flow is waiting.

## Pipeline stages

Workflows can be divided into sequential stages, not only for labeling but to throttle concurrency.

# Getting started

Read the [tutorial](TUTORIAL.md) to get started writing workflows.

There is also a [DZone Refcard](https://dzone.com/refcardz/continuous-delivery-with-jenkins-workflow).

# Installation

Releases are available on the Jenkins update center.
You need to be running a sufficiently recent Jenkins release: an LTS in the 1.580.x line or newer (currently 1.596.x for the latest updates), or a weekly release.
See the [changelog](CHANGES.md) for news.

For OSS Jenkins users, install _Workflow: Aggregator_ (its dependencies will be pulled in automatically).
You will need to restart Jenkins to complete installation.

CloudBees Jenkins Enterprise users get Workflow automatically as of the 14.11 (1.580.1.1) release.
Otherwise install _CloudBees Workflow: Aggregator_ from the update center.
Again dependencies will be pulled in automatically, including all the OSS plugins.

# News & questions

* [Changelog](CHANGES.md)
* [jenkins-workflow tag](http://stackoverflow.com/tags/jenkins-workflow) on StackOverflow
* [JIRA](https://issues.jenkins-ci.org/secure/IssueNavigator.jspa?reset=true&jqlQuery=project+%3D+JENKINS+AND+resolution+%3D+Unresolved+AND+%28component+%3D+workflow-plugin+OR+labels+in+%28workflow%29%29+ORDER+BY+component+ASC,+key+DESC&mode=hide) (file issues in the `workflow-plugin` component, or other components with the `workflow` label)
* [User list discussions](https://groups.google.com/forum/#!topicsearchin/jenkinsci-users/workflow) (mention `workflow` in the subject)
* [#JenkinsWorkflow](https://twitter.com/hashtag/JenkinsWorkflow) on Twitter

# Demo

See the [demo overview](demo/README.md) using Docker if you want to try a complete setup quickly. In short:

    docker run -p 8080:8080 -p 8081:8081 -p 8022:22 -ti jenkinsci/workflow-demo

and browse [localhost:8080](http://localhost:8080/).

# Presentations

Webinar _Continuous Delivery as Code with Jenkins Workflow_ (Sep 2015): [slides](https://www.cloudbees.com/sites/default/files/webinar-_continuous_delivery_as_code_with_jenkins_workflow.pdf) and [video](https://youtu.be/Q2pZdzaaCXg) (demo starts at 20:30)

Jenkins Workflow: What’s Up? (JUC West) (Sep 2015): [slides](http://www.slideshare.net/jgcloudbees/juc-west-15-jenkins-workflow-whats-up) and [video](https://youtu.be/VkIzoU7zYzE)

Jenkins Office Hour on Workflow for plugin developers (Aug 2015): [video](https://www.youtube.com/watch?v=4zdy7XGx3PA)

Workflow Meetup London (Mar 2015): [slides](http://www.slideshare.net/jgcloudbees/london-workflow-summit-kkjg)

Jenkins Workflow Screencast (Jan 2015): [video](https://www.youtube.com/watch?v=Welwf1wTU-w)

Webinar _Orchestrating the Continuous Delivery Process in Jenkins with Workflow_ (Dec 2014): [video](http://youtu.be/ZqfiW8eVcuQ)


# Detailed guides

[Reusing build steps from freestyle projects](basic-steps/CORE-STEPS.md)

[Using version control from a workflow](scm-step/README.md)

[Using the global Groovy class library](cps-global-lib/README.md)

# Development

* [Ongoing plugin compatibility list](COMPATIBILITY.md)
* [Source repository](https://github.com/jenkinsci/workflow-plugin)
* [Open pull requests](https://github.com/jenkinsci/workflow-plugin/pulls)
* [CI job](https://jenkins.ci.cloudbees.com/job/plugins/job/workflow-plugin/)
  [![Build Status](https://jenkins.ci.cloudbees.com/buildStatus/icon?job=plugins/workflow-plugin)](https://jenkins.ci.cloudbees.com/job/plugins/job/workflow-plugin/)
* [Video tutorial on implementing a Step API](http://jenkins-ci.org/content/workflow-plugin-tutorial-writing-step-impl)
* [Video walkthrough of code](https://www.youtube.com/watch?v=tZygoTlW6YE)

## Running from sources

If you want to try running recent development changes, rather than released binaries, you have two options. You can run directly from the source tree; from the root of the repository:

    mvn -DskipTests clean install && mvn -f aggregator hpi:run

Then visit http://localhost:8080/jenkins/ to play with the plugins.

(If your IDE supports compile-on-save mode this is especially convenient since each `hpi:run` will pick up compiled changes from member plugins without needing to run to `package` phase.)

You can also run the Docker demo with snapshot binaries:

    make -C demo run-snapshot

The snapshot Docker demo is mainly useful for verifying the effect of ongoing changes on future demo binary releases. You get the `cd` sample job set up, but your environment is thrown away if you kill the Docker container (for example with Ctrl-C). When using `hpi:run` the same `aggregator/work/` home directory is reused so long as you do not explicitly delete it.

## Source organization

While the implementation is divided into a number of plugins, for ease of prototyping they are all kept in one repository using snapshot dependencies.

* `step-api` defines a generic build step interface (not specific to flows) that many plugins could in the future depend on.
* `basic-steps` add some generic step implementations. There is [more documentation there](basic-steps/CORE-STEPS.md).
* `api` defines the essential aspects of flows and their executions. In particular, the engine running a flow is extensible and so could in the future support visual orchestration languages.
* `support` adds general implementations of some internals needed by flows, such as storing state.
* `job` provides the actual job type and top-level UI for defining and running flows.
* `durable-task-step` uses the `durable-task` plugin to define a shell script step that can survive restarts.
* `scm-step` adds SCM-related steps. There is [more documentation there](scm-step/README.md).
* `cps` is the flow engine implementation based on the Groovy language, and supporting long-running flows using a _continuation passing style_ transformation of the script.
* `cps-global-lib` adds a Git-backed repository for Groovy libraries available to scripts.
* `stm` is a simple engine implementation using a _state transition machine_, less intended for end users than as a reference for how engines can work. Currently only partly implemented.
* `aggregator` is a placeholder plugin allowing you to `mvn hpi:run` and see everything working together, as well as holding integration tests.
