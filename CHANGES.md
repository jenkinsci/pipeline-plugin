# Changelog

Only noting significant user changes, not internal code cleanups and minor bug fixes.

## 1.11 (Nov 12 2015)

* _Workflow: Multibranch_ plugin now released as nonbeta and available from the regular update center. (Currently not included in _Workflow: Aggregator_.)

## 1.11-beta-4 (Nov 09 2015)

* Minor enhancements to JENKINS-28769.

## 1.11-beta-3 (Nov 05 2015)

* [JENKINS-28769](https://issues.jenkins-ci.org/browse/JENKINS-28769): syntax highlighting, example scripts, and basic code snippets for Workflow scripts in the browser.
* When running the `build` step, the upstream log should now show a link to the downstream build.

## 1.11-beta-2 (Oct 26 2015)

* [JENKINS-25550](https://issues.jenkins-ci.org/browse/JENKINS-25550): flow builds hung due to a buggy step (and certain erroneous scripts) can now be forcibly stopped by using hyperlinks that appear in the console after an initial abort attempt.
* [JENKINS-30974](https://issues.jenkins-ci.org/browse/JENKINS-30974): error during build queue rendering on 1.624+ when using non-concurrent-capable Workflow builds.
* Added the `absoluteUrl` property to `RunWrapper`
* [JENKINS-29542](https://issues.jenkins-ci.org/browse/JENKINS-29542): fixed help display for `env` global variable.

## 1.11-beta-1 (Oct 07 2015)

* [JENKINS-30086](https://issues.jenkins-ci.org/browse/JENKINS-30086): improve inline help and display names for steps, and show step function names in _Snippet Generator_.
* [JENKINS-30346](https://issues.jenkins-ci.org/browse/JENKINS-30346): added a cross platform `deleteDir` step to recursively delete a directory and its contents.
* [JENKINS-30088](https://issues.jenkins-ci.org/browse/JENKINS-30088): Adjust how steps are displayed, to make the appearance cleaner and keep a focus on the DSL

## 1.10.1 (Oct 15 2015)

* [JENKINS-30974](https://issues.jenkins-ci.org/browse/JENKINS-30974): backport from 1.11.

## 1.10 (Aug 31 2015)

* [JENKINS-30122](https://issues.jenkins-ci.org/browse/JENKINS-30122): regression in usage of the Authorize Project plugin in 1.10-beta-1.
* [JENKINS-29739](https://issues.jenkins-ci.org/browse/JENKINS-29739): _Snippet Generator_ did not work for `build` with exactly one parameter.
* [JENKINS-29169](https://issues.jenkins-ci.org/browse/JENKINS-29169): `build('otherWorkflow').buildVariables` can now be used to access variables set using `env.KEY = 'value'` notation.

## 1.10-beta-1 (Aug 21 2015)

* [JENKINS-26942](https://issues.jenkins-ci.org/browse/JENKINS-26942): added `stash` and `unstash` steps (deprecating `unarchive`).
* [JENKINS-26135](https://issues.jenkins-ci.org/browse/JENKINS-26135): expand global library functionality to allow predefined variables and even custom DSLs.
* [JENKINS-29890](https://issues.jenkins-ci.org/browse/JENKINS-29890): `input` step submitter was not being consistently logged.
* [JENKINS-25879](https://issues.jenkins-ci.org/browse/JENKINS-25879), [JENKINS-29875](https://issues.jenkins-ci.org/browse/JENKINS-29875): New API to run long lived tasks that could block on I/O in a separate thread avoiding to block main CPS VM thread.
* [JENKINS-29653](https://issues.jenkins-ci.org/browse/JENKINS-29653): visual tweak to _Snippet Generator_.

## 1.9 (Aug 06 2015)

* _Running Steps_ link is now called _Workflow Steps_ as it will show steps for workflows that have long since completed.
* [JENKINS-29738](https://issues.jenkins-ci.org/browse/JENKINS-29738): TimeoutStep restarts the timeout value when `onResume` method is invoked
* [JENKINS-26163](https://issues.jenkins-ci.org/browse/JENKINS-26163): `AbstractStepExecutionImpl.onResume` was not (usually) being called for block-scoped steps, leading to incorrect behavior after Jenkins restart for flows inside `timeout` or `waitUntil`.
* [JENKINS-26761](https://issues.jenkins-ci.org/browse/JENKINS-26761): `NullPointerException` from Git commit notification requests under unknown circumstances; improved robustness and logging.
* Improvements to JENKINS-29221 fix from 1.9-beta-1.

Note: if you also have _CloudBees Workflow: Groovy Checkpoint_ installed, you _must_ upgrade it to 1.4.

## 1.9-beta-1 (Jul 27 2015)

* [JENKINS-26129](https://issues.jenkins-ci.org/browse/JENKINS-26129): Experimental support for multibranch workflows. (For now, in a separate plugin, not included in _Workflow: Aggregator_, since it depends on the Branch API plugin which does not have a non-beta release and so is available only from the experimental update center.)
* [JENKINS-28131](https://issues.jenkins-ci.org/browse/JENKINS-28131): pass `NODE_NAME` into `node {}`.
* [JENKINS-26860](https://issues.jenkins-ci.org/browse/JENKINS-26860): added _Execute concurrent builds if necessary_ option for Workflow projects.
* [JENKINS-28756](https://issues.jenkins-ci.org/browse/JENKINS-28756): dropdown for _General SCM_ step incorrectly listed SCMs not compatible with Workflow.
* [JENKINS-29221](https://issues.jenkins-ci.org/browse/JENKINS-29221): better robustness when the Groovy script is uncompilable.
* [JENKINS-29571](https://issues.jenkins-ci.org/browse/JENKINS-29571): corrupt build record after renaming a job.

## 1.8 (Jun 01 2015)

* Now based on Jenkins core 1.609.x.
* [JENKINS-24673](https://issues.jenkins-ci.org/browse/JENKINS-24673), [JENKINS-27392](https://issues.jenkins-ci.org/browse/JENKINS-27392): Added `wrap` step, permitting compatibility with plugins offering build wrappers. The first is the Xvnc plugin as of 1.22.
* [JENKINS-25938](https://issues.jenkins-ci.org/browse/JENKINS-25938): avoid creating a Java (native) thread for every executor (flow build or `node {}` block).
* [JENKINS-22941](https://issues.jenkins-ci.org/browse/JENKINS-22941): allow Jenkins safe restart to proceed even while a flow build is running, so long as it is just waiting (for an external process, `waitUntil`, etc.).
* [JENKINS-26900](https://issues.jenkins-ci.org/browse/JENKINS-26900): hide the “flyweight” executor on master corresponding to the flow build when it is just waiting.

## 1.7 (May 29 2015)

* [JENKINS-28317](https://issues.jenkins-ci.org/browse/JENKINS-28317): `withEnv` clobbered environment variables set in enclosing steps.
* Ability to make some steps (such as `catchError`) as “advanced/deprecated”.
* Ability to define global variables available to all scripts, with accompanying documentation, such as `env` and `currentBuild`.

## 1.6 (May 04 2015)

* [JENKINS-28063](https://issues.jenkins-ci.org/browse/JENKINS-28063): `build` step did not properly handle the case that two upstream builds could trigger the same downstream build.
* [JENKINS-28179](https://issues.jenkins-ci.org/browse/JENKINS-28179): honor `-Dhudson.slaves.WorkspaceList=<character>`
* [JENKINS-27571](https://issues.jenkins-ci.org/browse/JENKINS-27571): Fixed link in build sidepanel.
* API addition: `LauncherDecorator` can now be used in block-scoped steps, and there is more flexibility in handling exits from durable tasks.

## 1.5 (Apr 01 2015)

* Now based on Jenkins core 1.596.1.
* [JENKINS-27531](https://issues.jenkins-ci.org/browse/JENKINS-27531): critical startup error in 1.597+ loading build records migrated from before 1.597.
* [JENKINS-27695](https://issues.jenkins-ci.org/browse/JENKINS-27695): critical error in 1.607+ running `node` blocks.
* [JENKINS-26128](https://issues.jenkins-ci.org/browse/JENKINS-26128): added a `withEnv` step. `env.VAR = value` syntax remains supported but `withEnv` should be preferred.
* [JENKINS-27474](https://issues.jenkins-ci.org/browse/JENKINS-27474): added a `fileExists` step.
* [JENKINS-26552](https://issues.jenkins-ci.org/browse/JENKINS-26552) and thus [JENKINS-27389](https://issues.jenkins-ci.org/browse/JENKINS-27389): problems with environment variables fixed.
* Avoid some possible name clashes with function names in scripts (`build` reported).
* API addition: block-scoped steps can now pass in `EnvironmentExpander` and/or `ConsoleLogFilter` to better customize processing of nested code.

## 1.4.2 (Jul 21 2015)

* JENKINS-28131 backport from 1.9.

## 1.4.1 (Jul 29 2015)

* JENKINS-26761 robustness/diagnostics backport from 1.9.

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

## 1.0 (Nov 25 2014)

See [archives](https://github.com/jenkinsci/workflow-plugin/blob/cdca218ca11e127d97543a2e209803708c5af9d8/CHANGES.md) for changes in pre-1.0 betas.
