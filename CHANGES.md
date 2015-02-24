# Changelog

Only noting significant user-visible or major API changes, not internal code cleanups and minor bug fixes.

## 1.3 (upcoming)

### User changes
* JENKINS-25779: snippet generator now omits default values of complex steps.
* Ability to configure project display name.
* Fixing `java.io.NotSerializableException: org.jenkinsci.plugins.workflow.support.steps.StageStepExecution$CanceledCause` thrown from certain scripts using `stage`.
* JENKINS-27052: `stage` step did not prevent a third build from entering a stage after a second was unblocked by a first leaving it.
* JENKINS-26605: Missing link to _Full Log_ under _Running Steps_ when a single step produced >150Kb of output.
* `catchError` was incorrectly setting build status to failed when it was merely aborted, canceled, etc.
* PR 52: fixed some memory leaks causing the permanent generation and heap to grow unbounded after many flow builds.

## 1.2 (Jan 24 2015)

### User changes
* JENKINS-26101: the complete workflow script can now be loaded from an SCM repository of your choice.
* JENKINS-26149: the `build` step did not survive Jenkins restarts while running.
* JENKINS-25570: added `waitUntil` step.
* JENKINS-25924: added `error` step.
* JENKINS-26030: file locks could prevent build deletion.
* JENKINS-26074: completed parallel branches become invisible until the whole parallel step is done
* JENKINS-26541: rejected sandbox methods were not offered for approval when inside `parallel`.
* Snippet generator incorrectly suggested `pwd` when Groovy requires `pwd()`.
* JENKINS-26104: Custom Workflow step for sending mail

## 1.1 (Dec 09 2014)

### User changes
* `input` step did not survive Jenkins restarts.
* `env` did not work in sandbox mode.
* `load` step was not available in the _Snippet Generator_.
* `println` now automatically whitelisted.
* Incorrect build result (status) sometimes shown in log.
* `url:` can now be omitted from the `git` step when it is the only parameter.

## 1.0

No changes from 1.0-beta-1.

## 1.0-beta-1

### User changes
* Fixes to start time, duration, and similar aspects of flow runs.

### API changes
* `BodyInvoker` and `BodyExecutionCallback` replace `StepContext.invokeBodyLater` and `BodyExecution.addCallback` for better control of execution behavior.

## 0.1-beta-8

### User changes
* `scm` step renamed `checkout`.
* New syntax for structured objects, enabling more concise scripts without needing to import Jenkins-specific classes. _Incompatible syntax change_ for callers of `checkout` and `step` steps. Snippet generator and documentation updated to match.
* Added `timeout` step.
* Better cancellation behavior when the stop button is pressed, for example killing all branches of `parallel`.
* Fleshed out Groovy operators supported.
* Friendlier error in case a step is invoked without its required context, such as `sh` outside `node`.
* The flow graph table is now on its own page, accessible via the _Running Steps_ link from a build.
* A visual graph of flow nodes is available from a hidden link `graphViz`. Similarly, `flowGraph` links to a simple list of all nodes, including merge nodes that would not be visible in the regular table.
* More useful information about the graph in the REST API for a flow build.
* Fixes to Groovy Git library.
* Fixed form validation for Groovy script.
* Fixed thread leak.
* Script security fix when the Email Ext plugin is also installed.

### API changes
* `invokeBodyLater` now returns a new `BodyExecution`, mainly to support cancellation of the body.
* Fields in a `StepExecution` marked `@Inject` or `@StepContextParameter` will now be reinjected when resuming from disk.

## 0.1-beta-7
* Moved `input` and `build` steps to the `workflow-support` plugin (from the `workflow-basic-steps` plugin).
* Added a label to `build` step nodes based on the display name of the downstream job.
* Message was missing from _Paused for Input_ page.
* Changed snippet generator to quote multiline strings using `'''`; quoting with `/` (slashy strings) was not working well.
* Added `PauseAction` to the API for potential use from visualizations.
* Added support for `WorkspaceAction` to report labels, for potential use from visualizations.
* Fixed environment variable handling when running builds on slaves, which had regressed with the introduction of `env` in beta 5.
* No longer enforcing workspace-relative paths from `dir`, `readFile`, and `writeFile` steps.
* Added `FlowInterruptedException` to API.
* Showing the flow build as aborted, rather than failed, if a user rejects an `input` prompt.
* Flow termination from a `stage` step (due to a superseding build) can now be handled using `catch` and `finally` blocks.
* Fixed handling of `&&` and `||` operators in Groovy.
* Git-controlled Groovy library now expects sources to be under a directory `src/` rather than at top level. Also no longer need to pass a `this` reference to a helper class from the main script. See [documentation](cps-global-lib/README.md) for this feature.

## 0.1-beta-6
* Now based on Jenkins core 1.580.1.
* Elementary support for tracking workspaces used by a flow. Currently visible only by clicking on the flow node starting a `node` (or `ws`) step.
* Properly reporting the job owning an executor slot (`node` step); useful for CloudBees Folders Plus controlled slaves, and perhaps other plugins as well.
* Updated dependencies on Git and Subversion plugins to pick up important fixes.
* Some utility functions used by steps with unusual configuration factored out into a new API class `DescribableHelper`.

## 0.1-beta-5
* _Incompatible_: some steps formerly using `value` as the name for a principal parameter now use a more descriptive name. You can still call them without specifying the field name; the difference is only visible if you were also specifying other optional parameters.
* `evaluate` function may now be used to evaluate Groovy code CPS-transformed as if it were part of the main script. New `load` step lets you load and run a Groovy script from the workspace. A work in progress.
* New plugin `workflow-cps-global-lib` allowing a global shared library for scripts.
* Flow scripts may now be run in a Groovy sandbox to allow regular users to define them without administrator approval. More work is likely needed to supply a reasonable method call whitelist out of the box.
* Flow definition page has a section letting you visually configure a step and see the corresponding Groovy code to run it.
* Added some general inline help to the flow definition page regarding script syntax.
* Added `readFile` and `writeFile` steps to manipulate workspace files easily.
* Added `tool` step to run a tool installer on the current node.
* Added `bat` step to run commands on Windows nodes.
* `build` step now sets an appropriate cause on the downstream build.
* `build` step may now take parameters.
* Faster display of output from shell steps.
* Robustness improvements for shell steps, especially in the face of disconnecting or rebooting slaves.
* Improved handling of parameter binding for classes using `@DataBoundSetter`.
* Scripts can now read and write properties of a magic variable `env` to get and set environment variables applicable to `sh` and similar steps. Overridden variables are also visible from the REST API.
* Fixed icon in flow execution table.
* Not publishing `workflow-stm` plugin unless and until it is made functional. Existing installations may be deleted.

## 0.1-beta-4
* Requires Jenkins core 1.580 or later.
* Better behavior on cloud slaves integrated with the Durable Task plugin.
* Allowing a build in the middle of a shell step to be interrupted.
* Allowed `SCMStep` to be extended from other SCM plugins.
* Added `StageAction` and `TimingAction` for the benefit of visualizations.
* Allowing `build` to start builds of other workflows, not just freestyle projects.

## 0.1-beta-3
* Requires Jenkins core 1.577 or later.
* Support for running some builders and post-build actions from a flow using the same code as in freestyle projects. See [here](basic-steps/CORE-STEPS.md) for details.
* Added `catchError` step.

## 0.1-beta-2

* Simplified threading model for Groovy engine.
* Added `build` step.
* `StepExecution` now separated from `Step`.
* `steps.` prefix now optional (only needed to disambiguate defined steps from other user functions with the same name). `with.` prefix no longer in use.
* Requires Jenkins core 1.572 or later.
* Removed custom `mercurial` step; can now use a generic `scm` step (for example with the Mercurial plugin 1.51-beta-2 or later).
* Hyperlinking of prompts from the `input` step.

## 0.1-beta-1

First public release.
