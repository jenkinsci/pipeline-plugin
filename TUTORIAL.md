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

```groovy
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

```groovy
echo("hello from Workflow");
```

but you can also drop the semicolon (`;`), drop the parentheses (`(` and `)`), and use single quotes (`'`) instead of double (`"`) if you do not need to perform variable substitutions.

Comments in Groovy, like in Java, can use single-line or multiline styles:

```groovy
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

```groovy
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

### Windows variations

The instructions in this tutorial assume Jenkins is running on Linux or another Unix-like operating system.
If your Jenkins server (or, later, slave) are running on Windows, try using `bat` in place of `sh`, and use backslashes as the file separator where needed.
(Backslashes do generally need to be escaped inside strings.)
For example, rather than

```groovy
sh "${mvnHome}/bin/mvn -B verify"
```

you could use

```groovy
bat "${mvnHome}\\bin\\mvn -B verify"
```

## Syntax explained

`node` is a step which schedules a task to run by adding it to the Jenkins build queue.
As soon as an executor slot is available on some _node_ (the Jenkins master, or a slave), the task is run on that node.
`node` also allocates a _workspace_ (file directory) on that node for the duration of the task (more on this later).

Groovy functions can accept _closures_ (blocks of code), and some steps expect a block.
In this case the code between the braces (`{` and `}`) is the body of the `node` step.
Many steps (like `git` and `sh` in this example) can only run in the context of a node, so trying to run just

```groovy
sh 'echo oops'
```

as a flow script will not work: Jenkins does not know what system to run commands on.

Unlike user-defined functions, workflow steps always take named parameters. So

```groovy
git url: 'https://github.com/jglick/simple-maven-project-with-tests.git'
```

is passing one parameter, named `url` (the Git source code repository to check out).
This parameter happens to be mandatory; it also takes some other optional parameters such as `branch`.
You can pass as many as you need:

```groovy
git url: 'https://github.com/jglick/simple-maven-project-with-tests.git', branch: 'master'
```

As before, Groovy lets you omit parentheses around function arguments.
The named-parameter syntax is also a shorthand for creating a _map_, which in Groovy uses the syntax `[key1: value1, key2: value2]`, so you could also write:

```groovy
git([url: 'https://github.com/jglick/simple-maven-project-with-tests.git', branch: 'master'])
```

For convenience, when calling steps taking only one parameter (or only one mandatory parameter) you can omit the parameter name; so

```groovy
sh 'echo hello'
```

is really shorthand for

```groovy
sh([script: 'echo hello'])
```

The `tool` step makes sure a tool with the given name (in this case, a specific version of the Maven build tool) is installed on the current node.
But merely running this step does not do much good; the script needs to know _where_ it was installed, so the tool can be run later.
For this, we need a variable.

The `def` keywork in Groovy is the quickest way to define a new variable (with no specific type). So

```groovy
def mvnHome = tool 'M3'
```

makes sure `M3` is installed somewhere accessible to Jenkins, and assigns the return value of the step (an installation path) to the `mvnHome` variable.
We could also use a more Java-like syntax with a static type:

```groovy
String mvnHome = tool("M3");
```

Finally, we want to run our Maven build. When Groovy encounters `$` inside a double-quoted string

```groovy
"${mvnHome}/bin/mvn -B verify"
```

it replaces the `${mvnHome}` part with the value of that expression (here, just the variable value).
The more verbose Java-like syntax would be

```groovy
mvnHome + "/bin/mvn -B verify"
```

In the console output you will see the final command being run, for example

```
[flow] Running shell script
+ /path/to/jenkins/tools/hudson.tasks.Maven_MavenInstallation/M3/bin/mvn -B verify
```

## Managing the environment

Another way to use tools by default is to add them to your executable path, by using the special variable `env` defined for all workflows:

```groovy
node {
  git url: 'https://github.com/jglick/simple-maven-project-with-tests.git'
  def mvnHome = tool 'M3'
  env.PATH = "${mvnHome}/bin:${env.PATH}"
  sh 'mvn -B verify'
}
```

(Note: you cannot run the above script in the Groovy sandbox until Workflow 1.1 or later.)

Properties of this variable will be environment variables on the current node.
You can also override certain environment variables, and the overrides will be seen by subsequent `sh` steps (or anything else that pays attention to environment variables).
This is convenient because now we can run `mvn` without a fully-qualified path.

We will not use this style again, for reasons that will be explained later in more complex examples.

Some environment variables are defined by Jenkins by default, as for freestyle builds.
For example, `env.BUILD_TAG` can be used to get a tag like `jenkins-projname-1` from Groovy code, or `$BUILD_TAG` can be used from a `sh` script.

## Build parameters

If you have configured your workflow to accept parameters when it is built (_Build with Parameters_), these will be accessible as Groovy variables of the same name.

# Recording test results and artifacts

Rather than failing the build if there are some test failures, we would like Jenkins to record them, but then proceed.
We would also like to capture the JAR that we built.

```groovy
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

```groovy
def aa = new hudson.tasks.ArtifactArchiver('**/target/*.jar')
aa.fingerprint = true // i.e., aa.setFingerprint(true)
step aa
```

but this is cumbersome and does not work well with Groovy sandbox security, so any object-valued argument to a step may instead be given as a map.
Here

```groovy
[$class: 'ArtifactArchiver', artifacts: '**/target/*.jar', fingerprint: true]
```

specifies the values of the `artifacts` and `fingerprint` properties (controlling what files to save, and whether to also record fingerprints for them).
`$class` is used to pick the kind of object to create.
It may be a fully-qualified class name (`hudson.tasks.ArtifactArchiver`), but the simple name may be used when unambiguous.

In some cases part of a step configuration will force an object at a certain point to be of a fixed class, so `$class` can be omitted entirely.
For example, rather than using the simple `git` step, you can use the more general `checkout` step and specify any complex configuration supported by the Git plugin:

```groovy
checkout scm: [$class: 'GitSCM', branches: [[name: '*/master']], userRemoteConfigs: [[url: 'https://github.com/jglick/simple-maven-project-with-tests']]]
```

Here `[[name: '*/master']]` is an array with one map element, `[name: '*/master']`, which is an object of type `hudson.plugins.git.BranchSpec`, but we can omit `$class: 'BranchSpec'` since `branches` can only hold this kind of object.
Similarly, the elements of `userRemoteConfigs` are declared to be of type `UserRemoteConfig`, so this need not be mentioned.

# Using slaves

So far our workflow has run only on the Jenkins master, assuming you had no slaves configured.
You can even force it to run on the master by telling the `node` step this:

```groovy
node('master') {
    // as before
}
```

(Here we are passing a value for the optional `label` parameter of the step, as well as a body block.)

To create a simple slave, select _Manage Jenkins » Manage Nodes » New Node_ and create a _Dumb Slave_.
Leave _# of executors_ as 1.
Pick some _Remote root directory_ such as `/tmp/slave`.
Type `remote` in the _Labels_ field and set the _Launch method_ to _Launch slave agents via Java Web Start_.
_Save_, then click on the new slave and _Launch_.

Now go back to your flow definition and request this slave’s label:

```groovy
node('remote') {
    // as before
}
```

The parameter may be a slave name, or a single label, or even a label expression such as:

```groovy
node('unix && 64bit') {
    // as before
}
```

When you _Build Now_ you should see

```
Running on <yourslavename> in /<slaveroot>/workspace/<flowname>
```

and the `M3` Maven installation being unpacked to this slave root.

## Pausing; flyweight vs. heavyweight executors

Let us pause the script so we can take a better look at what is happening.

```groovy
node('remote') {
  input 'Ready to go?'
  // rest as before
}
```

The `input` step pauses flow execution.
Its default `message` parameter gives a prompt which will be shown to a human.
(You can optionally request information back, hence the name of the step.)

When you run a new build, you should see

```
Running: Input
Ready to go?
Proceed or Abort
```

If you click _Proceed_ the build will proceed as before.
But first go to the Jenkins main page and look at the _Build Executor Status_ widget.
You will see an unnumbered entry under _master_ called something like _flowname #10_; executors #1 and #2 on the master will be idle.
You will also see an entry under your slave, in a numbered row (probably #1) called _Building part of flowname #10_.

Why are there two executors consumed by one flow build?
Every flow build itself runs on the master, using a _flyweight executor_: an uncounted slot that is assumed to not take any significant computational power.
This executor represents the actual Groovy script, which almost all of the time is idle, waiting for a step to complete.
Flyweight executors are always available.

When you run a `node` step, a regular heavyweight executor is allocated on a node (usually a slave) matching the label expression, as soon as one is available.
This executor represents the real work being done on the node.
If you start a second build of the flow while the first is still paused with the one available executor, you will see both flow builds running on master.
But only the first will have grabbed the one available executor on the slave; the other _part of flowname #11_ will be shown in _Build Queue (1)_.
(After a moment the console log for the second build will note that it is still waiting for an available executor.)

To finish up, click the ▾ beside either executor entry for any running flow and select _Paused for Input_, then click _Proceed_.
(You can also click the link in the console output.)

## Workspaces

Besides waiting to allocate an executor on a node, the `node` step also automatically allocates a _workspace_: a directory specific to this job where you can check out sources, run commands, and do other work.
Workspaces are _locked_ for the duration of the step: only one build at a time can use a given workspace.
So if multiple builds need a workspace on the same node, additional workspaces will be allocated.

_Configure_ your slave, and set _# of executors_ to 2 (and _Save_).
Now start your build twice in a row.
The log for the second build will show

```
Running on <yourslavename> in /<slaveroot>/workspace/<flowname>@2
```

The `@2` shows that the build used a separate workspace from the first one, with which it ran concurrently.
You should also have seen

```
Cloning the remote Git repository
```

since this new workspace required a new copy of the project sources.

You can also use the `ws` step to explicitly ask for another workspace on the current slave, _without_ grabbing a new executor slot.
Inside its body all commands run in the second workspace.
The `dir` step can be used to run a block with a different working directory (typically a subdirectory of the workspace) without allocating a new workspace.

# Adding more complex logic

Your Groovy script can include functions, conditional tests, loops, `try`/`catch`/`finally` blocks, and so on.
Save this flow definition:

```groovy
node('remote') {
  git url: 'https://github.com/jglick/simple-maven-project-with-tests.git'
  def v = version()
  if (v) {
    echo "Building version ${v}"
  }
  def mvnHome = tool 'M3'
  sh "${mvnHome}/bin/mvn -B -Dmaven.test.failure.ignore verify"
  step([$class: 'ArtifactArchiver', artifacts: '**/target/*.jar', fingerprint: true])
  step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
}
def version() {
  def matcher = readFile('pom.xml') =~ '<version>(.+)</version>'
  matcher ? matcher[0][1] : null
}
```

Here we are using the `def` keyword to define a function (you can also give a Java type in place of `def`, to make it look more like a Java method).
`=~` is Groovy syntax to match text against a regular expression; `[0]` looks up the first match, and `[1]` the first `(…)` group within that match.

The `readFile` step loads a text file from the workspace and returns its content.
(Do _not_ try to use `java.io.File` methods, because these will refer to files on the master where Jenkins is running, not in the current workspace.)
There is also a `writeFile` step to save content to a text file in the workspace.

When you run the flow you should see

```
Building version 1.0-SNAPSHOT
```

(Unless your _Script Security_ plugin is version 1.11 or higher, you may see a `RejectedAccessException` error at this point.
If so, a Jenkins administrator will need to go to _Manage Jenkins » In-process Script Approval_ and _Approve_ `staticMethod org.codehaus.groovy.runtime.ScriptBytecodeAdapter findRegex java.lang.Object java.lang.Object`.
Then try running your script again and it should work.)

## Serialization of local variables

If you tried inlining the `version` function as follows

```groovy
node('remote') {
  git url: 'https://github.com/jglick/simple-maven-project-with-tests.git'
  def matcher = readFile('pom.xml') =~ '<version>(.+)</version>'
  if (matcher) {
    echo "Building version ${matcher[0][1]}"
  }
  def mvnHome = tool 'M3'
  sh "${mvnHome}/bin/mvn -B -Dmaven.test.failure.ignore verify"
  step([$class: 'ArtifactArchiver', artifacts: '**/target/*.jar', fingerprint: true])
  step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
}
```

you would have noticed a problem:

```
java.io.NotSerializableException: java.util.regex.Matcher
```

This occurs because the `matcher` local variable is of a type (`Matcher`) not considered serializable by Java.
Since workflows must survive Jenkins restarts, the state of the running program is periodically saved to disk so it can be resumed later.
(Saves occur after every step, or in the middle of some steps like `sh`.)
The “state” includes the whole control flow, including local variables, positions in loops, and so on.
Therefore any variable values used in your program should be numbers, strings, or other serializable types, not “live” objects such as network connections.

If you must use a nonserializable value temporarily, discard it before doing anything else.
When we kept the matcher only as a local variable inside a function, it was automatically discarded as soon as the function returned.
You can also explicitly discard a reference when you are done with it:

```groovy
node('remote') {
  git url: 'https://github.com/jglick/simple-maven-project-with-tests.git'
  def matcher = readFile('pom.xml') =~ '<version>(.+)</version>'
  if (matcher) {
    echo "Building version ${matcher[0][1]}"
  }
  matcher = null
  def mvnHome = tool 'M3'
  sh "${mvnHome}/bin/mvn -B -Dmaven.test.failure.ignore verify"
  step([$class: 'ArtifactArchiver', artifacts: '**/target/*.jar', fingerprint: true])
  step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
}
```

## Multiple threads

Workflows can use a `parallel` step to perform multiple actions at once.
This special step takes a map as its argument; keys are “branch names” (labels for your own benefit), and values are blocks to run.

To see how this can be useful, let us install a new plugin, _Parallel Test Executor_ (version 1.6 or later).
This plugin includes a workflow step that lets you split apart slow test runs.
Also make sure the JUnit plugin is at least version 1.3.

Now create a new workflow with the following script:

```groovy
node('remote') {
  git url: 'https://github.com/jenkinsci/parallel-test-executor-plugin-sample.git'
  archive 'pom.xml, src/'
}
def splits = splitTests([$class: 'CountDrivenParallelism', size: 2])
def branches = [:]
for (int i = 0; i < splits.size(); i++) {
  def exclusions = splits.get(i);
  branches["split${i}"] = {
    node('remote') {
      sh 'rm -rf *'
      unarchive mapping: ['pom.xml' : '.', 'src/' : '.']
      writeFile file: 'exclusions.txt', text: exclusions.join("\n")
      sh "${tool 'M3'}/bin/mvn -B -Dmaven.test.failure.ignore test"
      step([$class: 'JUnitResultArchiver', testResults: 'target/surefire-reports/*.xml'])
    }
  }
}
parallel branches
```

(Note: to enable the Groovy sandbox on this script, be sure to update the Script Security plugin to version 1.11 or later.
Even so, you may see a `RejectedAccessException` error at this point.
If so, a Jenkins administrator will need to go to _Manage Jenkins » In-process Script Approval_ and _Approve_ `staticMethod org.codehaus.groovy.runtime.ScriptBytecodeAdapter compareLessThan java.lang.Object java.lang.Object`.
Then try running your script again and it should work.
A later version of the plugin may remove the need for this workaround.)

When you run this flow for the first time, it will check out a project and run all of its tests in sequence.
The second time and subsequent times you run it, the `splitTests` task will partition your tests into two sets of roughly equal runtime.
The rest of the flow then runs these in parallel, so if you look at _trend_ (in the _Build History_ widget) you will see the second and subsequent builds taking roughly half the time of the first.
(If you only have the one slave configured with its two executors this would not really save any time, but you may have multiple slaves on different hardware matching the same label expression.)

This script is more complex than the previous ones so it bears some examination.
We start by grabbing a slave, checking out sources, and making a copy of them using the `archive` step;

```groovy
archive 'pom.xml, src/'
```

is shorthand for the more general

```groovy
step([$class: 'ArtifactArchiver', artifacts: 'pom.xml, src/'])
```

Later we will `unarchive` these same files back into _other_ workspaces.
We could have just run `git` anew in each slave’s workspace, but this would result in duplicated changelog entries, as well as contacting the Git server twice.
(A flow build is permitted to run as many SCM checkouts as it needs to, which is useful for projects working with multiple repositories, but not what we want here.)
More seriously, if someone pushed a new Git commit at just the wrong time, you might be testing different sources in some branches, which is prevented when we do the checkout just once and distribute sources to slaves ourselves.

`splitTests` returns a list of lists of strings.
From each (list) entry we construct one branch to run; the label (map key) is akin to a thread name, and will appear in the build log.
The Maven project is set up to expect a file `exclusions.txt` at its root, and it will run all tests _not_ mentioned there, which we set up via the `writeFile` step.
When we run the `parallel` step, each branch is started at the same time, and the overall step completes when all the branches finish: “fork & join”.

There are several new ideas at work here.
First of all, you can see that a single flow build allocates several executors, potentially on different slaves, at the same time.
(You can see these starting and finishing in the Jenkins executor widget on the main screen.)
Each call to `node` gets its own workspace.
This kind of flexibility is impossible in a freestyle project, each build of which is tied to exactly one workspace.
(The Parallel Test Executor plugin works around that for its freestyle build step by triggering multiple builds of the project, making the history hard to follow.)

Note also that we run `tool` inside each branch, rather than at top level, since Maven might be installed in a different place on each slave.
We would _not_ want to use `env` in this case

```groovy
env.PATH = "${mvnHome}/bin:${env.PATH}"
```

since environment variable overrides are currently limited to being global to a workflow run, not local to the current thread (and thus slave).

You may also have noticed that we are running `JUnitResultArchiver` several times, something that is not possible in a freestyle project.
The test results recorded in the build are cumulative.

When you view the log for a build with multiple branches, the output from each will be intermixed.
It can be useful to click on the _Running Steps_ link on the build’s sidebar.
This will display a tree-table view of all the steps run so far in the build, grouped by logical block, for example `parallel` branch.
You can click on individual steps and get more details, such as the log output for that step in isolation, the workspace associated with a `node` step, and so on.

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

Consult the [Docker demo](demo/README.md) for an example of a flow using multiple `stage`s.

# Loading script text from version control

Complex flows would be cumbersome to write and maintain in the textarea provided in the Jenkins job configuration.
Therefore it makes sense to load the program from another source, one that you can maintain using version control and standalone Groovy editors.

## Entire script from SCM

The easiest way to do this is to select _Groovy CPS DSL from SCM_ when defining the workflow.
In that case you do not enter any Groovy code in the Jenkins UI; you just indicate where in source code you want to retrieve the program.
(If you update this repository, a new build will be triggered, so long as your job is configured with an SCM polling trigger.)

## Manual loading

For some cases you may prefer to explicitly load Groovy script text from some source.
The standard Groovy `evaluate` function can be used, but most likely you will want to load a flow definition from a workspace.
For this purpose you can use the `load` step, which takes a filename in the workspace and runs it as Groovy source text.
The loaded file can either contain statements at top level, which are run immediately; or it can define functions and return `this`, in which case the result of the `load` step can be used to invoke those functions like methods.
An older version of the [Docker demo](demo/README.md) showed this technique in practice:

```groovy
def flow
node('slave') {
    git url: '…'
    flow = load 'flow.groovy'
    flow.devQAStaging()
}
flow.production()
```

where [flow.groovy](https://github.com/jenkinsci/workflow-plugin-pipeline-demo/blob/641a3491d49570f4f8b9e3e583eb71bad1aa493f/flow.groovy) defines `devQAStaging` and `production` functions (among others) before ending with

```groovy
return this;
```

The subtle part here is that we actually have to do a bit of work with the `node` and `git` steps just to check out a workspace so that we can `load` something.
In this case `devQAStaging` runs on the same node as the main source code checkout, while `production` runs outside of that block (and in fact allocates a different node).

## Global libraries

Injection of function and class names into a flow before it runs is handled by plugins, and one is bundled with workflow that allows you to get rid of the above boilerplate and keep the whole script (except one “bootstrap” line) in a Git server hosted by Jenkins.
A [separate document](cps-global-lib/README.md) has details on this system.

# Exploring available steps

There are a number of workflow steps not discussed in this document, and plugins can add more.
Even steps discussed here can take various special options that can be added from release to release.
To make it possible to browse all available steps and their syntax, a help tool is built into the flow definition screen.

Click _Snippet Generator_ beneath your script textarea.
You should see a list of installed steps.
Some will have a help icon (![help](https://raw.githubusercontent.com/jenkinsci/jenkins/master/war/src/main/webapp/images/16x16/help.png)) at the top which you can click to see general information.
There will also be UI controls to help you configure the step, in some cases with auto completion and other features found in Jenkins configuration screens.
(Again look for help icons on these.)

When you are done, click _Generate Groovy_ to see a Groovy snippet that would run the step exactly as you have configured it.
This lets you see the function name used for the step, and the names of any parameters it takes (if not a default parameter), and their syntax.
You can copy and paste the generated code right into your flow, or use it as a starting point (perhaps trimming some unnecessary optional parameters).
