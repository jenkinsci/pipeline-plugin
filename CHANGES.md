# Changelog

Only noting significant user-visible or major API changes, not internal code cleanups and minor bug fixes.

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
