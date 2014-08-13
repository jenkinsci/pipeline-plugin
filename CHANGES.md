# Changelog

Only noting significant user-visible or major API changes, not internal code cleanups and minor bug fixes.

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
