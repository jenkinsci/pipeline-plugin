# Introduction

Building continuous delivery pipelines and similarly complex tasks in Jenkins using freestyle projects and existing plugins is very awkward.
You need to mix Parameterized Trigger, Copy Artifact, Promoted Builds, Conditional Build Step, and more just to express what should be a simple script.
This project attempts to make it possible to directly write that script, what people often call a _workflow_ (sometimes abbreviated _flow_), while integrating with Jenkins features like slaves and publishers.

[JUC Boston slides](http://www.cloudbees.com/sites/default/files/2014-0618-Boston-Jesse_Glick-Workflow.pdf) and [video](https://www.youtube.com/watch?v=gpaV6x9QwDo&index=9&list=UUKlF3GIFy9KVUefVbycx_vw)

[Early slides](https://docs.google.com/a/cloudbees.com/presentation/d/1ysu71kGpEjvsikKAXdPXTJULadHh9cRbpd0gJaIkVtA)

# Core features

Specific status of implementation below.

## Scripted control flow

Your whole workflow is a single Groovy script using an embedded DSL, possibly quite short and legible; there is no need to jump between multiple job configuration screens to see what is going on.
Conditions, loops, variables, parallel tasks, and so on are defined using regular language constructs.
At any point you can insert a shell script to do “real work” (compilation, etc.).

## Jenkins model object integration

Standard DSL functions let you run external processes, grab slave nodes, allocate workspaces, build “legacy” (freestyle) jobs, and so on.

See [here](basic-steps/CORE-STEPS.md) for information on reusing build steps from freestyle projects.

## Pause and resume execution

If Jenkins is restarted (intentionally, or because of a crash) while your workflow is running, when it comes back up, execution is resumed where it left off.
This applies to external processes (shell scripts) so long as the slave can be reattached, and losing the slave connection temporarily is not fatal either.

Flows can pause in the middle and wait for a human to approve something, or enter some information.
Executors are not consumed while the flow is waiting.

## SCM integration

See [here](scm-step/README.md) for details on using version control from a workflow.

## Pipeline stages

By default, flow builds can run concurrently.
The `stage` command lets you mark certain sections of a build as being constrained by limited concurrency (or, later, unconstrained).
Newer builds are always given priority when entering such a throttled stage; older builds will simply exit early if they are preëmpted.

A concurrency of one is useful to let you lock a singleton resource, such as deployment to a single target server.
Only one build will deploy at a given time: the newest which passed all previous stages.

A finite concurrency ≥1 can also be used to prevent slow build stages such as integration tests from overloading the system.
Every SCM push can still trigger a separate build of a quicker earlier stage as compilation and unit tests.
Yet each build runs linearly and can even retain a single workspace, avoiding the need to identify and copy artifacts between builds.
(Even if you dispose of a workspace from an earlier stage, you can retain information about it using simple local variables.)

## Example script

```
node('linux') { // grab a slave and allocate a workspace
  git url: '…' // clone/checkout
  sh 'mvn verify' // run your build
}
```

A more complete example still in a single workspace:

```
node('windows_jnlp') {
  svn url: 'https://…/trunk/…'
  bat(/
echo off
set JAVA_HOME=c:\Program Files\Java\jdk1.7.0_60
c:\Program Files\Maven\bin\mvn clean install
/)
  step $class: 'hudson.tasks.ArtifactArchiver', artifacts: '**/target/*-SNAPSHOT*', fingerprint: true
  step $class: 'hudson.tasks.junit.JUnitResultArchiver', testResults: '**/target/surefire-reports/*.xml'
}
```

# Installation

If you do not want to build from sources, early releases (see the [changelog](CHANGES.md) for details on which) are available on the Jenkins experimental update center.
You need to be running a recent Jenkins weekly release, currently 1.580 or newer.

For OSS Jenkins users, follow [these instructions](http://jenkins-ci.org/content/experimental-plugins-update-center) and install _Workflow: Aggregator_ (its dependencies will be pulled in automatically).
You will need to restart Jenkins to complete installation.

Jenkins Enterprise by CloudBees users can click _Enable access_ under _Access to experimental plugin releases_ in the main Jenkins configuration screen, and then install _CloudBees Workflow: Aggregator_.
Again dependencies will be pulled in automatically, including all the OSS plugins.

# Demo

See the [demo overview](demo/README.md) using Docker if you want to try a complete setup quickly.

# Development

* [Changelog](CHANGES.md)
* [Source repository](https://github.com/jenkinsci/workflow-plugin)
* [JIRA](https://issues.jenkins-ci.org/secure/IssueNavigator.jspa?reset=true&jqlQuery=project+%3D+JENKINS+AND+resolution+%3D+Unresolved+AND+component+%3D+workflow+ORDER+BY+priority+DESC&mode=hide)
* [Trello board](https://trello.com/b/u2fJQnDX/workflow) tracking active and proposed tasks.
* [CI job](https://jenkins.ci.cloudbees.com/job/plugins/job/workflow-plugin/) with validated merge support.
  [![Build Status](https://jenkins.ci.cloudbees.com/buildStatus/icon?job=plugins/workflow-plugin)](https://jenkins.ci.cloudbees.com/job/plugins/job/workflow-plugin/)
* [Video tutorial on implementing a Step API](http://jenkins-ci.org/content/workflow-plugin-tutorial-writing-step-impl)
* [Video walkthrough of code](https://www.youtube.com/watch?v=tZygoTlW6YE)

# Source organization

While the implementation is divided into a number of plugins, for ease of prototyping they are all kept in one repository using snapshot dependencies.

* `step-api` defines a generic build step interface (not specific to flows) that many plugins could in the future depend on.
* `basic-steps` add some generic step implementations.
* `api` defines the essential aspects of flows and their executions. In particular, the engine running a flow is extensible and so could in the future support visual orchestration languages.
* `support` adds general implementations of some internals needed by flows, such as storing state.
* `job` provides the actual job type and top-level UI for defining and running flows.
* `durable-task-step` uses the `durable-task` plugin to define a shell script step that can survive restarts.
* `scm-step` adds SCM-related steps. There is [more documentation there](scm-step/README.md).
* `cps` is the flow engine implementation based on the Groovy language, and supporting long-running flows using a _continuation passing style_ transformation of the script.
* `cps-global-lib` adds a Git-backed repository for Groovy libraries available to scripts.
* `stm` is a simple engine implementation using a _state transition machine_, less intended for end users than as a reference for how engines can work. Currently only partly implemented.
* `aggregator` is a placeholder plugin allowing you to `mvn hpi:run` and see everything working together, as well as holding integration tests.
