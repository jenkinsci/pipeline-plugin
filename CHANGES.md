# Changelog

Only noting significant user-visible or major API changes, not internal code cleanups and minor bug fixes.

## 1.6 (upcoming)

* Added `discardOldBuilds` option to the `stage` step to disable preemption of older builds.
* [JENKINS-27571](https://issues.jenkins-ci.org/browse/JENKINS-27571) Fixed link in build sidepanel.
* API addition: `LauncherDecorator` can now be used in block-scoped steps, and there is more flexibility in handling exits from durable tasks.

## 1.5 (Apr 01 2015)

* Now based on Jenkins core 1.596.1.
* [JENKINS-27531](https://issues.jenkins-ci.org/browse/JENKINS-27531): critical startup error in 1.597+ loading build records migrated from before 1.597.
* [JENKINS-27695](https://issues.jenkins-ci.org/browse/JENKINS-27695): critical error in 1.607+ running `node` blocks.
* [JENKINS-26128](https://issues.jenkins-ci.org/browse/JENKINS-26128) added a `withEnv` step. `env.VAR = value` syntax remains supported but `withEnv` should be preferred.
* [JENKINS-27474](https://issues.jenkins-ci.org/browse/JENKINS-27474): added a `fileExists` step.
* Avoid some possible name clashes with function names in scripts (`build` reported).
* API addition: block-scoped steps can now pass in `EnvironmentExpander` and/or `ConsoleLogFilter` to better customize processing of nested code.

## 1.4 (Mar 16 2015)

* [JENKINS-26034](https://issues.jenkins-ci.org/browse/JENKINS-26034): added `failFast` option to the `parallel` step.
* [JENKINS-26085](https://issues.jenkins-ci.org/browse/JENKINS-26085): added `credentialsId` to the `git` step.
* [JENKINS-26121](https://issues.jenkins-ci.org/browse/JENKINS-26121): record the approver of an `input` step in build history.
* [JENKINS-26122](https://issues.jenkins-ci.org/browse/JENKINS-26122): Prepend `parallel` step execution logs with the branch label.
* [JENKINS-26072](https://issues.jenkins-ci.org/browse/JENKINS-26072): you can now specify a custom workspace location to lock in a `ws` step.
* [JENKINS-26692](https://issues.jenkins-ci.org/browse/JENKINS-26692): add `quietPeriod` option for the `build` step.
* [JENKINS-26619](https://issues.jenkins-ci.org/browse/JENKINS-26619): _Snippet Generator_ did not work on Git SCM extensions.
* [JENKINS-27145](https://issues.jenkins-ci.org/browse/JENKINS-27145): showing available environment variables from help.
* [JENKINS-26834](https://issues.jenkins-ci.org/browse/JENKINS-26834): `currentBuild` can be used to refer to the running build, examine the status of its predecessor, etc.
* [JENKINS-25851](https://issues.jenkins-ci.org/browse/JENKINS-25851): the `build` step (in the default `wait: true` mode) now returns a handle to the downstream build. You may also set `propagate: false` to proceed even if that build is not stable.

## 1.3 (Mar 04 2015)

* [JENKINS-25958](https://issues.jenkins-ci.org/browse/JENKINS-25958): the basic `node` step did not work if Workflow was dynamically installed in Jenkins (with no restart).
* [JENKINS-26363](https://issues.jenkins-ci.org/browse/JENKINS-26363): anyone permitted to cancel a flow build should also be permitted to cancel an `input` step.
* [JENKINS-26093](https://issues.jenkins-ci.org/browse/JENKINS-26093): `build` can now accept `parameters` in a more uniform (and sandbox-friendly) syntax, and the _Snippet Generator_ proposes them based on the actual parameter definitions of the downstream job.
* [JENKINS-25784](https://issues.jenkins-ci.org/browse/JENKINS-25784): Sandbox mode defauling based on RUN_SCRIPTS privileges.
* [JENKINS-25890](https://issues.jenkins-ci.org/browse/JENKINS-25890): deadlock during restart.
* Fixed some file handle leaks caught by tests which may have affected Windows masters.
* [JENKINS-25779](https://issues.jenkins-ci.org/browse/JENKINS-25779): snippet generator now omits default values of complex steps.
* Ability to configure project display name.
* Fixing `java.io.NotSerializableException: org.jenkinsci.plugins.workflow.support.steps.StageStepExecution$CanceledCause` thrown from certain scripts using `stage`.
* [JENKINS-27052](https://issues.jenkins-ci.org/browse/JENKINS-27052): `stage` step did not prevent a third build from entering a stage after a second was unblocked by a first leaving it.
* [JENKINS-26605](https://issues.jenkins-ci.org/browse/JENKINS-26605): Missing link to _Full Log_ under _Running Steps_ when a single step produced >150Kb of output.
* [JENKINS-26513](https://issues.jenkins-ci.org/browse/JENKINS-26513): deserialization error when restarting Jenkins inside `node {}` while it is still waiting for a slave to come online.
* `catchError` was incorrectly setting build status to failed when it was merely aborted, canceled, etc.
* [JENKINS-26123](https://issues.jenkins-ci.org/browse/JENKINS-26123): added `wait` option to `build`.
* Check for failure to even trigger a build from `build`.
* [PR 52](https://github.com/jenkinsci/workflow-plugin/pull/52): fixed some memory leaks causing the permanent generation and heap to grow unbounded after many flow builds.
* [JENKINS-26120](https://issues.jenkins-ci.org/browse/JENKINS-26120): added `sleep` step.

## 1.2 (Jan 24 2015)

* [JENKINS-26101](https://issues.jenkins-ci.org/browse/JENKINS-26101): the complete workflow script can now be loaded from an SCM repository of your choice.
* [JENKINS-26149](https://issues.jenkins-ci.org/browse/JENKINS-26149): the `build` step did not survive Jenkins restarts while running.
* [JENKINS-25570](https://issues.jenkins-ci.org/browse/JENKINS-25570): added `waitUntil` step.
* [JENKINS-25924](https://issues.jenkins-ci.org/browse/JENKINS-25924): added `error` step.
* [JENKINS-26030](https://issues.jenkins-ci.org/browse/JENKINS-26030): file locks could prevent build deletion.
* [JENKINS-26074](https://issues.jenkins-ci.org/browse/JENKINS-26074): completed parallel branches become invisible until the whole parallel step is done
* [JENKINS-26541](https://issues.jenkins-ci.org/browse/JENKINS-26541): rejected sandbox methods were not offered for approval when inside `parallel`.
* Snippet generator incorrectly suggested `pwd` when Groovy requires `pwd()`.
* [JENKINS-26104](https://issues.jenkins-ci.org/browse/JENKINS-26104): Custom Workflow step for sending mail

## 1.1 (Dec 09 2014)

* `input` step did not survive Jenkins restarts.
* `env` did not work in sandbox mode.
* `load` step was not available in the _Snippet Generator_.
* `println` now automatically whitelisted.
* Incorrect build result (status) sometimes shown in log.
* `url:` can now be omitted from the `git` step when it is the only parameter.

## 1.0

No changes from 1.0-beta-1.
See [here](https://github.com/jenkinsci/workflow-plugin/blob/cdca218ca11e127d97543a2e209803708c5af9d8/CHANGES.md) for changes in pre-1.0 betas.
