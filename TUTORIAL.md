This document is intended for new users of the Workflow feature to learn how to write and understand flows.

# Getting started

You need to be running Jenkins 1.580.1 or later.
If you have not already done so, make sure Workflow is installed: go to the Plugin Manager and install _Workflow: Aggregator_ and restart Jenkins.

If you are running Jenkins Enterprise 14.11 or later, you already have Workflow (plus some extra associated features).

If you want to play with Workflow without installing Jenkins separately (or touching your production system), try running the [Docker demo](demo/README.md).

# Creating a workflow

Click _New Item_, pick a name for your flow, select _Workflow_, and click _OK_.
You will be taken to the configuration screen for the flow.

The most important part here is the _Script_ textarea, where your flow script will be defined.
(Later we will learn about other options.)
Let us start with a trivial script:

```
echo 'hello from Workflow'
```

Also you should click the _Use Groovy Sandbox_ option if you are not a Jenkins administrator (read [here](https://wiki.jenkins-ci.org/display/JENKINS/Script+Security+Plugin#ScriptSecurityPlugin-GroovySandboxing) if you are curious what this means).

_Save_ your workflow when you are done.
Click _Build Now_ to run it.
You should see `#1` under _Build History_; click the ▾ and select _Console Output_ to see the output:

```
Started by user anonymous
Running: Print Message
hello from Workflow
Running: End of Workflow
Finished: SUCCESS
```

# A simple flow

So now let us do something useful, but no more complex than what you could do with a freestyle project.

## Some setup

First, make sure a Maven installation is available to do builds with.
Go to _Jenkins » Manage Jenkins » Configure System_, click _Add Maven_, give it the name _M3_ and let it be installed automatically.

Also if you do not have Git installed on your Jenkins server, try clicking _Delete Git_ on the default Git installation, and _Add Git » JGit_ to replace it.

Finally _Save_.

## Checking out and building sources

Now click on your flow and _Configure_ it to edit its script.

```
node() {
  git url: 'https://github.com/jglick/simple-maven-project-with-tests.git'
  def mvnHome = tool 'M3'
  sh "${mvnHome}/bin/mvn -B verify"
}
```

If you run this script, it should check out a Git repository and run Maven to build it.
Some tests will be run, which might (at random) pass or fail or be skipped.
If they fail, the `mvn` command will fail and your flow run will end with

```
ERROR: script returned exit code 1
Finished: FAILURE
```

## Syntax explained

TODO

## Windows variations

The preceding instructions assume Jenkins is running on Linux.
If you are on Windows, try:

```
node() {
  git url: 'https://github.com/jglick/simple-maven-project-with-tests.git'
  def mvnHome = tool 'M3'
  bat "${mvnHome}\\bin\\mvn -B verify"
}
```

For the rest of this tutorial, only the Linux form will be given.

# Recording test results and artifacts

Rather than failing the build if there are some test failures, we would like Jenkins to record them, but then proceed.
We would also like to capture the JAR that we built.

```
node() {
  git url: 'https://github.com/jglick/simple-maven-project-with-tests.git'
  def mvnHome = tool 'M3'
  sh "${mvnHome}/bin/mvn -B -Dmaven.test.failure.ignore verify"
  step([$class: 'ArtifactArchiver', artifacts: '**/target/*.jar', fingerprint: true])
  step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
}
```

Now if tests fail, the flow will just be marked unstable (yellow ball), and you can browse the _Test Result Trend_ to see the history.
You should also see _Last Successful Artifacts_ on the flow index page.

## Syntax explained

TODO

# Using slaves

TODO

# Exploring available steps

Click _Snippet Generator_ beneath your script textarea.

TODO

# Stages

By default, flow builds can run concurrently.
The `stage` command lets you mark certain sections of a build as being constrained by limited concurrency (or, later, unconstrained).
Newer builds are always given priority when entering such a throttled stage; older builds will simply exit early if they are preëmpted.

A concurrency of one is useful to let you lock a singleton resource, such as deployment to a single target server.
Only one build will deploy at a given time: the newest which passed all previous stages.

A finite concurrency ≥1 can also be used to prevent slow build stages such as integration tests from overloading the system.
Every SCM push can still trigger a separate build of a quicker earlier stage as compilation and unit tests.
Yet each build runs linearly and can even retain a single workspace, avoiding the need to identify and copy artifacts between builds.
(Even if you dispose of a workspace from an earlier stage, you can retain information about it using simple local variables.)
