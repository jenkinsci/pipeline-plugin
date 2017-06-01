# Plugin Compatibility with Pipeline

For architectural reasons, plugins providing various extensions of interest to builds cannot be made automatically compatible with Pipeline.
Typically they require use of some newer APIs, large or small (see the bottom of this document for details).
This document captures the ongoing status of plugins known to be compatible or incompatible.

Entries list the class name serving as the entry point to the relevant functionality of the plugin (generally an `@Extension`), the plugin short name, and implementation status.

Newly filed issues should bear the label `pipeline` for ease of tracking.

## SCMs

- [X] `GitSCM` (`git`): supported as of 2.3; native `git` step also bundled
- [X] `SubversionSCM` (`subversion`): supported as of 2.5; native `svn` step also bundled
- [X] `MercurialSCM` (`mercurial`): supported as of 1.51
- [X] `PerforceScm` (`p4`, not the older `perforce`): supported as of 1.2.0
- [ ] `DimensionsSCM` (`dimensionsscm`): [JENKINS-26165](https://issues.jenkins-ci.org/browse/JENKINS-26165)
- [X] `IntegritySCM` (`integrity-plugin`): supported as of 1.36
- [X] `RepoScm` (`repo`): supported as of 1.9.0
- [X] `teamconcert`: supported as of 1.9.4
- [X] `CVSSCM` (`cvs`): scheduled to be supported in 2.13
- [X] `TeamFoundationServerScm` (`tfs`): supported as of 5.3.4
- [X] `AccuRevSCM` (`accurev`): supported as of 0.7.10

## Build steps and post-build actions

- [X] `ArtifactArchiver` (core)
- [X] `Fingerprinter` (core)
- [X] `JUnitResultArchiver` (`junit`)
- [X] `JavadocArchiver` (`javadoc`)
- [X] `Mailer` (`mailer`)
- [X] `CopyArtifact` (`copyartifact`): [JENKINS-24887](https://issues.jenkins-ci.org/browse/JENKINS-24887) in 1.34
- [ ] `DeployPublisher` (`deployer-framework`): [JENKINS-25976](https://issues.jenkins-ci.org/browse/JENKINS-25976)
- [X] Analysis publishers (e.g., `FindBugsPublisher`): supported as of `analysis-core` 1.73 and downstream plugins (e.g., `findbugs` 4.62)
- [X] `CoberturaPublisher` (`cobertura`): as of cobertura-1.10
- [ ] `Ant` (`ant`): [JENKINS-26056](https://issues.jenkins-ci.org/browse/JENKINS-26056)
- [ ] `Maven` (home TBD): [JENKINS-26057](https://issues.jenkins-ci.org/browse/JENKINS-26057)
- [ ] `XShellBuilder` (`xshell`): [JENKINS-26169](https://issues.jenkins-ci.org/browse/JENKINS-26169)
- [ ] ~~`DockerBuilder` (`docker-build-step`): [JENKINS-26178](https://issues.jenkins-ci.org/browse/JENKINS-26178)~~
- [X] `CucumberTestResultArchiver` (`cucumber-testresult-plugin`): supported as of 0.9.6
- [X] `HtmlPublisher` (`htmlpublisher`): supported as of 1.6
- [X] `HttpRequest` (`http_request`): supported as of 1.8.11
- [X] `JacocoPublisher` (`jacoco`): supported as of 2.1.0
- [X] `Publisher` (`testng`): supported as of 1.14
- [ ] `Gradle` (`gradle`): [JENKINS-27393](https://issues.jenkins-ci.org/browse/JENKINS-27393)
- [X] `CloverPublisher` (`clover`): supported as of 4.6.0
- [ ] `CloverPHPPublisher` (`cloverphp`): [JENKINS-37068](https://issues.jenkins-ci.org/browse/JENKINS-37068)
- [ ] `MsBuildBuilder` (`msbuild`): [JENKINS-26948](https://issues.jenkins-ci.org/browse/JENKINS-26948)
- [X] `HipChatNotifier` (`hipchat`): supported as of 1.0.0
- [ ] `IronMQNotifier` (`ironmq-notifier`): [JENKINS-35505](https://issues.jenkins-ci.org/browse/JENKINS-35505)
- [X] `LogParserPublisher` (`log-parser`): supported as of 2.0
- [X] `SlackNotifier` (`slack`): supported as of 2.0
- [ ] ~~`DescriptionSetterPublisher` (`description-setter`): [PR 7](https://github.com/jenkinsci/description-setter-plugin/pull/7)~~
- [ ] `CopyToSlaveBuildWrapper` and `CopyToMasterNotifier` (`copy-to-slave`): [JENKINS-28386](https://issues.jenkins-ci.org/browse/JENKINS-28386)
- [ ] `VeracodeNotifier` (`veracode-scanner`): [JENKINS-28387](https://issues.jenkins-ci.org/browse/JENKINS-28387)
- [X] `SeleniumHtmlReportPublisher` (`seleniumhtmlreport`): supported as of 1.0
- [ ] `GitPublisher` (`git`) or a custom step: [JENKINS-28335](https://issues.jenkins-ci.org/browse/JENKINS-28335)
- [X] SonarQube Jenkins: supported as of 2.5
- [X] `VSphereBuildStepContainer` (`vsphere-cloud`): supported as of 2.13
- [X] `ScoveragePublisher` (`scoverage`): supported as of 1.2.0
- [ ] `AWSCodeDeployPublisher` (`codedeploy`): [issue 36](https://github.com/awslabs/aws-codedeploy-plugin/issues/36)
- [X] `AnsiblePlaybookBuilder` (`ansible`): supported as of 0.4
- [X] `GitHubCommitNotifier`, `GitHubSetCommitStatusBuilder` (`github`): scheduled to be supported as of 1.14.3
- [ ] `CoverityPublisher` (`coverity`): [JENKINS-32354](https://issues.jenkins-ci.org/browse/JENKINS-32354)
- [X] `XUnitPublisher` and `XUnitBuilder` (`xunit`): scheduled to be supported as of 1.100
- [ ] `PerformancePublisher` (`performance`): scheduled to be supported as of 3.1
- [ ] `ZfjReporter` (`zephyr-for-jira-test-management`): [JENKINS-32801](https://issues.jenkins-ci.org/browse/JENKINS-32801)
- [ ] `BapSshPublisher` (`publish-over-ssh`): [JENKINS-27963](https://issues.jenkins-ci.org/browse/JENKINS-27963)
- [X] `PerfSigRecorder` and 5 more (`performance-signature-dynatrace`): supported as of 2.0
- [X] `StashNotifier` (`stashNotifier`): [supported as of 1.11](https://github.com/jenkinsci/stashnotifier-plugin#note-on-pipeline-plugin-usage)
- [X] `LambdaUploadBuildStep`, `LambdaInvokeBuildStep`, `LambdaEventSourceBuildStep` (`aws-lambda`): supported as of 0.5.0
- [X] `CucumberReportPublisher` (`cucumber-reports`): supported as of 2.1.0
- [ ] `Powershell` (`powershell`): [JENKINS-34581](https://issues.jenkins-ci.org/browse/JENKINS-34581)
- [X] `TestPackageBuilder` (+6 more), `ATXPublisher` (+4 more) (`ecutest`): supported as of 1.11
- [X] `GatlingPublisher` (`gatling`): supported as of 1.2.0
- [X] `BitbucketBuildStatusNotifier`: (`bitbucket-build-status-notifier`) supported as of 1.3
- [X] `NexusArtifactUploader` (`nexus-artifact-uploader`): supported as of 2.2
- [ ] `CppcheckPublisher` (`cppcheck`): [JENKINS-34943](https://issues.jenkins-ci.org/browse/JENKINS-35096)
- [ ] `ConfluencePublisher` (`confluence-publisher`): [JENKINS-36345](https://issues.jenkins-ci.org/browse/JENKINS-36345)
- [ ] `ValgrindPublisher` (`valgrind`): [JENKINS-34981](https://issues.jenkins-ci.org/browse/JENKINS-34981)
- [X] `HockeyappRecorder` (`hockeyapp`): supported as of 1.2.2
- [X] `WsCleanup` (`ws-cleanup`): supported as of 0.30
- [X] `XCodeBuilder` (`xcode-plugin`): supported as of 2.0.0

## Build wrappers

- [X] `ConfigFileBuildWrapper` (`config-file-provider`): supported as of 2.9.1
- [X] `Xvnc` (`xvnc`) supported as of 1.22
- [X] `BuildUser` (`build-user-vars`): supported as of 1.5
- [ ] `DashboardBuilder` (`environment-dashboard`): [issue 20](https://github.com/vipinsthename/environment-dashboard/issues/20)
- [X] `TimestamperBuildWrapper` (`timestamper`): supported as of 1.7
- [x] `MaskPasswordsBuildWrapper` (`mask-passwords`): supported as of 2.8
- [X] `XvfbBuildWrapper` (`xvfb`): supported as of 1.1.0-beta-1
- [X] `GCloudBuildWrapper` (`gcloud-sdk`): scheduled to be supported as of 0.0.2
- [X] `NodeJSBuildWrapper` (`nodejs`): supported as of 1.1.0
- [X] `AnsiColorBuildWrapper` (`ansicolor`): supported as of 0.4.2
- [ ] `CustomToolInstallWrapper` (`custom-tools-plugin`): [JENKINS-30680](https://issues.jenkins-ci.org/browse/JENKINS-30680)
- [ ] `PortAllocator` (`port-allocator`): [JENKINS-31449](https://issues.jenkins-ci.org/browse/JENKINS-31449)

## Triggers

- [X] `gerrit-trigger`: supported as of 2.15.0
- [X] `ghprb`: supported as of 1.36
- [X] `github`: supported as of 1.14.0
- [ ] `xtrigger-plugin`: [JENKINS-27301](https://issues.jenkins-ci.org/browse/JENKINS-27301)
- [X] `deployment-notification`: scheduled to be supported as of 1.3
- [X] `gitlab-plugin`: supported as of 1.1.26
- [X] `bitbucket`: supported as of 1.1.2
- [ ] `bitbucket-pullrequest-builder`: [JENKINS-31749](https://issues.jenkins-ci.org/browse/JENKINS-31749)
- [ ] `xtrigger`: [JENKINS-31933](https://issues.jenkins-ci.org/browse/JENKINS-31933)
- [X] `jira-trigger`: supported as of 0.4.0

## Clouds

- [ ] `elasticbox`: [JENKINS-25978](https://issues.jenkins-ci.org/browse/JENKINS-25978) (could also include build wrapper integration)
- [ ] `mansion-cloud`: [JENKINS-24815](https://issues.jenkins-ci.org/browse/JENKINS-24815)
- [X] `mock-slave` (for prototyping): supported as of 1.7
- [X] `docker`: supported as of 0.8
- [X] `nectar-vmware` (CloudBees Jenkins Enterprise): supported as of 4.3.2
- [X] `operations-center-cloud` (CloudBees Jenkins Enterprise/Operations Center): supported as of 1.7.5/1.8.3
- [X] `ec2`: known to work as is

## Miscellaneous

- [X] `rebuild`: supported as of 1.24
- [X] `parameterized-trigger` (to support a pipeline as downstream): supported as of 2.28
- [X] `build-token-root`: supported as of 1.2
- [X] `build-failure-analyzer`: supported as of 1.15
- [ ] `shelve-project`: [JENKINS-26432](https://issues.jenkins-ci.org/browse/JENKINS-26432)
- [X] `job-dsl`: Pipeline creation supported as of 1.29
- [X] `zentimestamp`: basic compatibility in 4.2
- [X] `claim`: scheduled to be supported as of 2.8
- [X] `ListSubversionTagsParameterValue` (`subversion`): supported as of 2.5.6
- [X] `authorize-project`: supported as of 1.1.0
- [X] `lockable-resources`: supported as of 1.8 (except for [JENKINS-34273](https://issues.jenkins-ci.org/browse/JENKINS-34273) and [JENKINS-34268](https://issues.jenkins-ci.org/browse/JENKINS-34268))
- [X] `customize-build-now`: supported as of 1.1
- [X] `test-results-analyzer` : supported as of 0.3.4
- [X] `embeddable-build-status`: scheduled to be supported as of 1.9
- [X] `groovy-postbuild`: supported as of 2.3
- [X] `jira` : supported as of 2.2
- [X] `ownership` : supported as of 0.9.0 ([Documentation](https://github.com/jenkinsci/ownership-plugin/blob/master/doc/PipelineIntegration.md))
- [ ] `job-restrictions`: partial support, [JENKINS-32355](https://issues.jenkins-ci.org/browse/JENKINS-32355)
- [X] `buildtriggerbadge`: supported as of 2.2
- [X] `build-monitor-plugin`: supported as of 1.6+build.159
- [X] `radiatorview`: supported as of 1.25
- [ ] `chucknorris`: [JENKINS-32594](https://issues.jenkins-ci.org/browse/JENKINS-32594)
- [ ] `sidebar-link`: [JENKINS-33458](https://issues.jenkins-ci.org/browse/JENKINS-33458)
- [x] `throttle-concurrent-builds`: supported as of 2.0 ([Documentation](https://github.com/jenkinsci/throttle-concurrent-builds-plugin#throttling-in-jenkins-pipeline))
- [x] `Exclusion`: supported as of 0.11
- [X] `jobconfighistory`: supported as of 2.14
- [X] `next-build-number`: supported as of 1.4
- [ ] `tracking-svn` [JENKINS-38060](https://issues.jenkins-ci.org/browse/JENKINS-38060)
- [x] `PrioritySorter`: supported as of 3.5.0
- [ ] `scoring-load-balancer`: [JENKINS-41267](https://issues.jenkins-ci.org/browse/JENKINS-41267)
- [ ] `envinject-plugin`: partial support, [JENKINS-42614](https://issues.jenkins-ci.org/browse/JENKINS-42614) ([Documentation](https://wiki.jenkins-ci.org/display/JENKINS/EnvInject+Plugin#EnvInjectPlugin-JenkinsPipeline))

## Custom steps

For cases when a first-class Pipeline step (rather than an adaptation of functionality applicable to freestyle projects) makes sense.

- [X] `docker-workflow`: DSL based on `docker` global variable
- [X] `credentials-binding`: `withCredentials` step as of 1.3
- [X] `ssh-agent`: `sshagent` step as of 1.8
- [X] `parallel-test-executor`: `splitTests` step since 1.6
- [ ] `gerrit-trigger`: [JENKINS-26102](https://issues.jenkins-ci.org/browse/JENKINS-26102), [JENKINS-26103](https://issues.jenkins-ci.org/browse/JENKINS-26103)
- [X] `mailer`: `mail` step in Pipeline 1.2
- [X] `artifactory`: step as of 2.5.0
- [X] `email-ext`: `emailext` step since 2.41
- [X] `marathon`: `marathon` step since 1.2.1
- [x] `Release Plugin` (`release`): step since 2.7
- [ ] `M2 Release Plugin` (`m2release`): [JENKINS-40766](https://issues.jenkins-ci.org/browse/JENKINS-40766)

# Plugin Developer Guide

Moved to a [separate document](DEVGUIDE.md).
