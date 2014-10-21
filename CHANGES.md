# Changelog

Only noting significant user-visible or major API changes, not internal code cleanups and minor bug fixes.

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
