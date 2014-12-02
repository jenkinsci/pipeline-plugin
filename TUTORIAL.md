This document is intended for new users of the Workflow feature to learn how to write and understand flows.

# Getting started

You need to be running Jenkins 1.580.1 or later.
If you have not already done so, make sure Workflow is installed: go to the Plugin Manager and install _Workflow: Aggregator_ and restart Jenkins.
Also make sure the _Git_ and _JUnit_ plugins are installed and up to date.

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

## Flow scripts explained

A workflow is a Groovy script which tells Jenkins what to do when your flow is run.
If you are not familiar with Groovy, it is a scripting-friendly language related to Java ([documentation](http://groovy-lang.org/documentation.html)).
You do not need to know much general Groovy to use Workflow; relevant bits of syntax will be introduced as needed.

For this example, it suffices to know that `echo` is a _step_: a function defined in a Jenkins plugin and made available to all workflows.
Groovy functions can use a C/Java-like syntax:

    echo("hello from Workflow");

but you can also drop the semicolon (`;`), drop the parentheses (`(` and `)`), and use single quotes (`'`) instead of double (`"`) if you do not need to perform variable substitutions.

Comments in Groovy, like in Java, can use single-line or multiline styles:

```
/*
 * Copyright 2014 Yoyodyne, Inc.
 */
// FIXME write this flow
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
node {
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

`node` is a step which schedules a task to run by adding it to the Jenkins build queue.
As soon as an executor slot is available on some _node_ (the Jenkins master, or a slave), the task is run on that node.
`node` also allocates a _workspace_ (file directory) on that node for the duration of the task (more on this later).

Groovy functions can accept _closures_ (blocks of code), and some steps expect a block.
In this case the code between the braces (`{` and `}`) is the body of the `node` step.
Many steps (like `git` and `sh` in this example) can only run in the context of a node, so trying to run just

    sh 'echo oops'

as a flow script will not work: Jenkins does not know what system to run commands on.

Unlike user-defined functions, workflow steps always take named parameters. So

    git url: 'https://github.com/jglick/simple-maven-project-with-tests.git'

is passing one parameter, named `url` (the Git source code repository to check out).
This parameter happens to be mandatory; it also takes some other optional parameters such as `branch`.
You can pass as many as you need:

    git url: 'https://github.com/jglick/simple-maven-project-with-tests.git', branch: 'master'

As before, Groovy lets you omit parentheses around function arguments.
The named-parameter syntax is also a shorthand for creating a _map_, which in Groovy uses the syntax `[key1: value1, key2: value2]`, so you could also write:

    git([url: 'https://github.com/jglick/simple-maven-project-with-tests.git', branch: 'master'])

For convenience, when calling steps taking only one parameter (or only one mandatory parameter) you can omit the parameter name; so

    sh 'echo hello'

is really shorthand for

    sh([script: 'echo hello'])

The `tool` step makes sure a tool with the given name (in this case, a specific version of the Maven build tool) is installed on the current node.
But merely running this step does not do much good; the script needs to know _where_ it was installed, so the tool can be run later.
For this, we need a variable.

The `def` keywork in Groovy is the quickest way to define a new variable (with no specific type). So

    def mvnHome = tool 'M3'

makes sure `M3` is installed somewhere accessible to Jenkins, and assigns the return value of the step (an installation path) to the `mvnHome` variable.
We could also use a more Java-like syntax with a static type:

    String mvnHome = tool("M3");

Finally, we want to run our Maven build. When Groovy encounters `$` inside a double-quoted string

    "${mvnHome}/bin/mvn -B verify"

it replaces the `${mvnHome}` part with the value of that expression (here, just the variable value).
The more verbose Java-like syntax would be

    mvnHome + "/bin/mvn -B verify"

In the console output you will see the final command being run, for example

```
[flow] Running shell script
+ /path/to/jenkins/tools/hudson.tasks.Maven_MavenInstallation/M3/bin/mvn -B verify
```

## Windows variations

The preceding instructions assume Jenkins is running on Linux.
If you are on Windows, try:

```
node {
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
node {
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

`-Dmaven.test.failure.ignore` is a Maven option to allow the `mvn` command to exit normally (status 0), so that the flow continues, even when some test failures are recorded on disk.

Next we run the `step` step twice.
This step just allows you to use certain build (or post-build) steps already defined in Jenkins for use in traditional projects.
It takes one parameter (called `delegate` but omitted here), whose value is a standard Jenkins build step.
We could create the delegate using Java constructor/method calls, using Groovy or Java syntax:

    def aa = new hudson.tasks.ArtifactArchiver('**/target/*.jar')
    aa.fingerprint = true // i.e., aa.setFingerprint(true)
    step aa

but this is cumbersome and does not work well with Groovy sandbox security, so any object-valued argument to a step may instead be given as a map.
Here

    [$class: 'ArtifactArchiver', artifacts: '**/target/*.jar', fingerprint: true]

specifies the values of the `artifacts` and `fingerprint` properties (controlling what files to save, and whether to also record fingerprints for them).
`$class` is used to pick the kind of object to create.
It may be a fully-qualified class name (`hudson.tasks.ArtifactArchiver`), but the simple name may be used when unambiguous.

In some cases part of a step configuration will force an object at a certain point to be of a fixed class, so `$class` can be omitted entirely.
For example, rather than using the simple `git` step, you can use the more general `checkout` step and specify any complex configuration supported by the Git plugin:

    checkout scm: [$class: 'GitSCM', branches: [[name: '*/master']], userRemoteConfigs: [[url: 'https://github.com/jglick/simple-maven-project-with-tests']]]

Here `[[name: '*/master']]` is an array with one map element, `[name: '*/master']`, which is an object of type `hudson.plugins.git.BranchSpec`, but we can omit `$class: 'BranchSpec'` since `branches` can only hold this kind of object.
Similarly, the elements of `userRemoteConfigs` are declared to be of type `UserRemoteConfig`, so this need not be mentioned.

# Using slaves

TODO passing a label to `node`
TODO distinction between flyweight master task, and heavyweight node tasks

## Workspaces

TODO workspace locks vs. concurrent builds
TODO `readFile` and `writeFile`
TODO `ws`, `dir`

# Exploring available steps

Click _Snippet Generator_ beneath your script textarea.

TODO

# Adding more complex logic

TODO loops, functions, try-catch, etc.
TODO serializable local variables
TODO `parallel`
TODO multiple slaves

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
