# Changelog

Only noting significant user-visible or major API changes, not internal code cleanups and minor bug fixes.

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
