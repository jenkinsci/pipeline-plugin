# Plugin Compatibility with Workflow

For architectural reasons, plugins providing various extensions of interest to builds cannot be made automatically compatible with Workflow.
Typically they require use of some newer APIs, large or small.
This document captures the ongoing status of plugins known to be compatible or incompatible.

Entries list the class name serving as the entry point to the relevant functionality of the plugin (generally an `@Extension`), the plugin short name, and implementation status.

## SCMs

See [this guide](scm-step/README.md#supporting-workflow-from-an-scm-plugin) for making `SCM`s compatible.

- [X] `GitSCM` (`git`): supported as of 2.3; native `git` step also bundled
- [X] `SubversionSCM` (`subversion`): supported as of 2.5; native `svn` step also bundled
- [X] `MercurialSCM` (`mercurial`): supported as of 1.51
- [X] `PerforceScm` (`p4`, not the older `perforce`): supported as of 1.2.0
- [ ] `DimensionsSCM` (`dimensionsscm`): [JENKINS-26165](https://issues.jenkins-ci.org/browse/JENKINS-26165)
- [ ] `IntegritySCM` (`integrity-plugin`): [JENKINS-27140](https://issues.jenkins-ci.org/browse/JENKINS-27140)
- [ ] `RepoScm` (`repo`): [JENKINS-26836](https://issues.jenkins-ci.org/browse/JENKINS-26836)
- [ ] `teamconcert`: [JENKINS-27464](https://issues.jenkins-ci.org/browse/JENKINS-27464)
- [X] `CVSSCM` (`cvs`): scheduled to be supported in 2.13

## Build steps and post-build actions

See [this guide](basic-steps/CORE-STEPS.md#adding-support-from-plugins) for making `Builder`s and `Publisher`s compatible.

- [X] `ArtifactArchiver` (core)
- [X] `Fingerprinter` (core)
- [X] `JUnitResultArchiver` (`junit`)
- [X] `JavadocArchiver` (`javadoc`)
- [X] `Mailer` (`mailer`)
- [X] `CopyArtifact` (`copyartifact`): [JENKINS-24887](https://issues.jenkins-ci.org/browse/JENKINS-24887) in 1.34
- [ ] `DeployPublisher` (`deployer-framework`): [JENKINS-25976](https://issues.jenkins-ci.org/browse/JENKINS-25976)
- [ ] `FindBugsPublisher` and similar (`analysis-core` and downstream): [JENKINS-25977](https://issues.jenkins-ci.org/browse/JENKINS-25977)
- [ ] `ExtendedEmailPublisher` (`email-ext`): [PR 97](https://github.com/jenkinsci/email-ext-plugin/pull/97)
- [ ] `Ant` (`ant`): [JENKINS-26056](https://issues.jenkins-ci.org/browse/JENKINS-26056)
- [ ] `Maven` (home TBD): [JENKINS-26057](https://issues.jenkins-ci.org/browse/JENKINS-26057)
- [ ] `XShellBuilder` (`xshell`): [JENKINS-26169](https://issues.jenkins-ci.org/browse/JENKINS-26169)
- [ ] `DockerBuilder` (`docker-build-step`): [JENKINS-26178](https://issues.jenkins-ci.org/browse/JENKINS-26178)
- [ ] `CucumberTestResultArchiver` (`cucumber-testresult-plugin`): [JENKINS-26340](https://issues.jenkins-ci.org/browse/JENKINS-26340)
- [ ] `HtmlPublisher` (`htmlpublisher`): [JENKINS-26343](https://issues.jenkins-ci.org/browse/JENKINS-26343)
- [ ] `JaCoCoPublisher` (`jacoco`): [JENKINS-27120](https://issues.jenkins-ci.org/browse/JENKINS-27120)
- [ ] `Publisher` (`testng`): [JENKINS-27121](https://issues.jenkins-ci.org/browse/JENKINS-27121)
- [ ] `GroovyPostbuildRecorder` (`groovy-postbuild`): [JENKINS-26918](https://issues.jenkins-ci.org/browse/JENKINS-26918)
- [ ] `Gradle` (`gradle`): [JENKINS-27393](https://issues.jenkins-ci.org/browse/JENKINS-27393)
- [ ] `CloverPublisher` (`clover`): [JENKINS-27302](https://issues.jenkins-ci.org/browse/JENKINS-27302)
- [ ] `MsBuildBuilder` (`msbuild`): [JENKINS-26948](https://issues.jenkins-ci.org/browse/JENKINS-26948)
- [ ] `HipChatNotifier` (`hipchat`): [JENKINS-27202](https://issues.jenkins-ci.org/browse/JENKINS-27202)
- [ ] `LogParserPublisher` (`log-parser`): [JENKINS-27208](https://issues.jenkins-ci.org/browse/JENKINS-27208)
- [ ] `SlackNotifier` (`slack`): [JENKINS-27652](https://issues.jenkins-ci.org/browse/JENKINS-27652)
- [ ] `DescriptionSetterPublisher` (`description-setter`): [PR 7](https://github.com/jenkinsci/description-setter-plugin/pull/7)
- [ ] `CopyToSlaveBuildWrapper` and `CopyToMasterNotifier` (`copy-to-slave`): [JENKINS-28386](https://issues.jenkins-ci.org/browse/JENKINS-28386)
- [ ] `VeracodeNotifier` (`veracode-scanner`): [JENKINS-28387](https://issues.jenkins-ci.org/browse/JENKINS-28387)
- [ ] `SeleniumHtmlReportPublisher` (`seleniumhtmlreport`): [JENKINS-28388](https://issues.jenkins-ci.org/browse/JENKINS-28388)
- [ ] `GitPublisher` (`git`) or a custom step: [JENKINS-28335](https://issues.jenkins-ci.org/browse/JENKINS-28335)

## Build wrappers

- [ ] `ConfigFileBuildWrapper` (`config-file-provider`): [JENKINS-26339](https://issues.jenkins-ci.org/browse/JENKINS-26339)
- [X] `Xvnc` (`xvnc`) supported as of 1.22
- [ ] `BuildUser` (`build-user-vars`): [JENKINS-26953](https://issues.jenkins-ci.org/browse/JENKINS-26953)
- [ ] `DashboardBuilder` (`environment-dashboard`): [issue 20](https://github.com/vipinsthename/environment-dashboard/issues/20)
- [ ] `TimestamperBuildWrapper` (`timestamper`): [JENKINS-27207](https://issues.jenkins-ci.org/browse/JENKINS-27207)
- [ ] `MaskPasswordsBuildWrapper` (`mask-passwords`): [PR 3](https://github.com/jenkinsci/mask-passwords-plugin/pull/3)
- [ ] `SSHAgentBuildWrapper` (`ssh-agent`): [JENKINS-28689](https://issues.jenkins-ci.org/browse/JENKINS-28689)

## Triggers

Implement `Trigger<ParameterizedJobMixIn.ParameterizedJob>` and implement `TriggerDescriptor.isApplicable` accordingly.

- [ ] `gerrit-trigger`: [JENKINS-26010](https://issues.jenkins-ci.org/browse/JENKINS-26010)
- [ ] `ghprb`: [JENKINS-26591](https://issues.jenkins-ci.org/browse/JENKINS-26591)
- [ ] `github`: [JENKINS-27136](https://issues.jenkins-ci.org/browse/JENKINS-27136)
- [ ] `xtrigger-plugin`: [JENKINS-27301](https://issues.jenkins-ci.org/browse/JENKINS-27301)
- [ ] `deployment-notification`: [JENKINS-28632](https://issues.jenkins-ci.org/browse/JENKINS-28632)

## Clouds

Do not necessarily need any special integration, but are encouraged to use `OnceRetentionStrategy` from `durable-task` to allow flow builds to survive restarts.

- [ ] `elasticbox`: [JENKINS-25978](https://issues.jenkins-ci.org/browse/JENKINS-25978) (could also include build wrapper integration)
- [ ] `mansion-cloud`: [JENKINS-24815](https://issues.jenkins-ci.org/browse/JENKINS-24815)
- [ ] `mock-slave` (for prototyping): [JENKINS-25090](https://issues.jenkins-ci.org/browse/JENKINS-25090)
- [X] `docker`: supported as of 0.8
- [X] `nectar-vmware` (CloudBees Jenkins Enterprise): supported as of 4.3.2
- [ ] `operations-center-cloud` (CloudBees Jenkins Enterprise/Operations Center): RM-2642
- [X] `ec2`: known to work as is

## Miscellaneous

- [X] `rebuild`: [JENKINS-26024](https://issues.jenkins-ci.org/browse/JENKINS-26024)
- [ ] `parameterized-trigger` (to support a workflow as downstream): [JENKINS-26050](https://issues.jenkins-ci.org/browse/JENKINS-26050)
- [X] `build-token-root`: [JENKINS-26693](https://issues.jenkins-ci.org/browse/JENKINS-26693)
- [X] `credentials-binding`: `withCredentials` step as of 1.3
- [ ] `build-failure-analyzer`: [JENKINS-27123](https://issues.jenkins-ci.org/browse/JENKINS-27123)
- [ ] `shelve-project`: [JENKINS-26432](https://issues.jenkins-ci.org/browse/JENKINS-26432)
- [X] `job-dsl`: implemented in 1.29
- [ ] `zentimestamp`: [JENKINS-26958](https://issues.jenkins-ci.org/browse/JENKINS-26958)
- [ ] `claim`: [JENKINS-27206](https://issues.jenkins-ci.org/browse/JENKINS-27206)
- [ ] `ListSubversionTagsParameterValue` (`subversion`): [JENKINS-27718](https://issues.jenkins-ci.org/browse/JENKINS-27718)
- [ ] `authorize-project`: [JENKINS-26670](https://issues.jenkins-ci.org/browse/JENKINS-26670)

## Custom steps

For cases when a first-class Workflow step (rather than an adaptation of functionality applicable to freestyle projects) makes sense.

- [X] `parallel-test-executor`: supported with `splitTests` step since 1.6
- [ ] `gerrit-trigger`: [JENKINS-26102](https://issues.jenkins-ci.org/browse/JENKINS-26102), [JENKINS-26103](https://issues.jenkins-ci.org/browse/JENKINS-26103)
- [X] `mailer`: [JENKINS-26104](https://issues.jenkins-ci.org/browse/JENKINS-26104) in Workflow 1.2
