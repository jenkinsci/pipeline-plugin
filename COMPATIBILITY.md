# Plugin Compatibility with Workflow

For architectural reasons, plugins providing various extensions of interest to builds cannot be made automatically compatible with Workflow.
Typically they require use of some newer APIs, large or small.
This document captures the ongoing status of plugins known to be compatible or incompatible.

## SCMs

See [this guide](scm-step/README.md#supporting-workflow-from-an-scm-plugin) for making `SCM`s compatible.

- [X] `GitSCM` (`git`): supported as of 2.3; native `git` step also bundled
- [X] `SubversionSCM` (`subversion`): supported as of 2.5; native `svn` step also bundled
- [X] `MercurialSCM` (`mercurial`): supported as of 1.51
- [ ] `PerforceScm` (`p4`, not the older `perforce`): [JENKINS-24206](https://issues.jenkins-ci.org/browse/JENKINS-24206)
- [ ] `DimensionsSCM` (`dimensionsscm`): [JENKINS-26165](https://issues.jenkins-ci.org/browse/JENKINS-26165)

## Build steps and post-build actions

See [this guide](basic-steps/CORE-STEPS.md#adding-support-from-plugins) for making `Builder`s and `Publisher`s compatible.

- [X] `ArtifactArchiver` (core)
- [X] `Fingerprinter` (core)
- [X] `JUnitResultArchiver` (`junit`)
- [X] `JavadocArchiver` (`javadoc`)
- [X] `Mailer` (`mailer`)
- [X] `CopyArtifact` (`copyartifact`): [JENKINS-24887](https://issues.jenkins-ci.org/browse/JENKINS-24887) in 1.34
- [ ] `DeployPublisher` (`deployer-framework`): [JENKINS-25976](https://issues.jenkins-ci.org/browse/JENKINS-25976)
- [ ] `FindBugsPublisher` (`findbugs`): [JENKINS-25977](https://issues.jenkins-ci.org/browse/JENKINS-25977)
- [ ] `ExtendedEmailPublisher` (`email-ext`): [PR 97](https://github.com/jenkinsci/email-ext-plugin/pull/97)
- [ ] `Ant` (`ant`): [JENKINS-26056](https://issues.jenkins-ci.org/browse/JENKINS-26056)
- [ ] `Maven` (home TBD): [JENKINS-26057](https://issues.jenkins-ci.org/browse/JENKINS-26057)
- [ ] `XShellBuilder` (`xshell`): [JENKINS-26169](https://issues.jenkins-ci.org/browse/JENKINS-26169)
- [ ] `DockerBuilder` (`docker-build-step`): [JENKINS-26178](https://issues.jenkins-ci.org/browse/JENKINS-26178)
- [ ] `CucumberTestResultArchiver` (`cucumber-testresult-plugin`): [JENKINS-26340](https://issues.jenkins-ci.org/browse/JENKINS-26340)
- [ ] `HtmlPublisher` (`htmlpublisher`): [JENKINS-26343](https://issues.jenkins-ci.org/browse/JENKINS-26343)

## Build wrappers

- [ ] API to integrate these: [JENKINS-24673](https://issues.jenkins-ci.org/browse/JENKINS-24673)
- [ ] `ConfigFileBuildWrapper` (`config-file-provider`): [JENKINS-26339](https://issues.jenkins-ci.org/browse/JENKINS-26339)
- [ ] `Xvnc` (`xvnc`): [JENKINS-26477](https://issues.jenkins-ci.org/browse/JENKINS-26477)

## Triggers

Implement `Trigger<ParameterizedJobMixIn.ParameterizedJob>` and implement `TriggerDescriptor.isApplicable` accordingly.

- [ ] `gerrit-trigger`: [JENKINS-26010](https://issues.jenkins-ci.org/browse/JENKINS-26010)

## Clouds

Do not necessarily need any special integration, but are encouraged to use `OnceRetentionStrategy` from `durable-task` to allow flow builds to survive restarts.

- [ ] `elasticbox`: [JENKINS-25978](https://issues.jenkins-ci.org/browse/JENKINS-25978) (could also include build wrapper integration)
- [ ] `mansion-cloud`: [JENKINS-24815](https://issues.jenkins-ci.org/browse/JENKINS-24815)
- [ ] `mock-slave` (for prototyping): [JENKINS-25090](https://issues.jenkins-ci.org/browse/JENKINS-25090)
- [X] `docker`: supported as of 0.8
- [X] `nectar-vmware` (Jenkins Enterprise): supported as of 4.3.2
- [ ] `operations-center-cloud` (Jenkins Enterprise/Operations Center): RM-2642

## Miscellaneous

- [X] `rebuild`: [JENKINS-26024](https://issues.jenkins-ci.org/browse/JENKINS-26024)
- [ ] `parameterized-trigger` (to support a workflow as downstream): [JENKINS-26050](https://issues.jenkins-ci.org/browse/JENKINS-26050)
- [ ] `build-token-root`: [JENKINS-26693](https://issues.jenkins-ci.org/browse/JENKINS-26693)
- [X] `credentials-binding`: `withCredentials` step as of 1.3

## Custom steps

For cases when a first-class Workflow step (rather than an adaptation of functionality applicable to freestyle projects) makes sense.

- [X] `parallel-test-executor`: supported with `splitTests` step since 1.6
- [ ] `gerrit-trigger`: [JENKINS-26102](https://issues.jenkins-ci.org/browse/JENKINS-26102), [JENKINS-26103](https://issues.jenkins-ci.org/browse/JENKINS-26103)
- [X] `mailer`: [JENKINS-26104](https://issues.jenkins-ci.org/browse/JENKINS-26104) in Workflow 1.2
