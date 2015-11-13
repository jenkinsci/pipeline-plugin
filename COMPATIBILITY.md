# Plugin Compatibility with Workflow

For architectural reasons, plugins providing various extensions of interest to builds cannot be made automatically compatible with Workflow.
Typically they require use of some newer APIs, large or small (see the bottom of this document for details).
This document captures the ongoing status of plugins known to be compatible or incompatible.

Entries list the class name serving as the entry point to the relevant functionality of the plugin (generally an `@Extension`), the plugin short name, and implementation status.

## SCMs

- [X] `GitSCM` (`git`): supported as of 2.3; native `git` step also bundled
- [X] `SubversionSCM` (`subversion`): supported as of 2.5; native `svn` step also bundled
- [X] `MercurialSCM` (`mercurial`): supported as of 1.51
- [X] `PerforceScm` (`p4`, not the older `perforce`): supported as of 1.2.0
- [ ] `DimensionsSCM` (`dimensionsscm`): [JENKINS-26165](https://issues.jenkins-ci.org/browse/JENKINS-26165)
- [X] `IntegritySCM` (`integrity-plugin`): supported as of 1.36
- [ ] `RepoScm` (`repo`): [JENKINS-26836](https://issues.jenkins-ci.org/browse/JENKINS-26836)
- [X] `teamconcert`: supported as of 1.9.4
- [X] `CVSSCM` (`cvs`): scheduled to be supported in 2.13

## Build steps and post-build actions

- [X] `ArtifactArchiver` (core)
- [X] `Fingerprinter` (core)
- [X] `JUnitResultArchiver` (`junit`)
- [X] `JavadocArchiver` (`javadoc`)
- [X] `Mailer` (`mailer`)
- [X] `CopyArtifact` (`copyartifact`): [JENKINS-24887](https://issues.jenkins-ci.org/browse/JENKINS-24887) in 1.34
- [ ] `DeployPublisher` (`deployer-framework`): [JENKINS-25976](https://issues.jenkins-ci.org/browse/JENKINS-25976)
- [X] Analysis publishers (e.g., `FindBugsPublisher`): supported as of `analysis-core` 1.73 and downstream plugins (e.g., `findbugs` 4.62)
- [ ] `ExtendedEmailPublisher` (`email-ext`): [PR 107](https://github.com/jenkinsci/email-ext-plugin/pull/107)
- [ ] `Ant` (`ant`): [JENKINS-26056](https://issues.jenkins-ci.org/browse/JENKINS-26056)
- [ ] `Maven` (home TBD): [JENKINS-26057](https://issues.jenkins-ci.org/browse/JENKINS-26057)
- [ ] `XShellBuilder` (`xshell`): [JENKINS-26169](https://issues.jenkins-ci.org/browse/JENKINS-26169)
- [ ] ~~`DockerBuilder` (`docker-build-step`): [JENKINS-26178](https://issues.jenkins-ci.org/browse/JENKINS-26178)~~
- [ ] `CucumberTestResultArchiver` (`cucumber-testresult-plugin`): [JENKINS-26340](https://issues.jenkins-ci.org/browse/JENKINS-26340)
- [X] `HtmlPublisher` (`htmlpublisher`): supported as of 1.6
- [ ] `JaCoCoPublisher` (`jacoco`): [JENKINS-27120](https://issues.jenkins-ci.org/browse/JENKINS-27120)
- [ ] `Publisher` (`testng`): [JENKINS-27121](https://issues.jenkins-ci.org/browse/JENKINS-27121)
- [ ] `GroovyPostbuildRecorder` (`groovy-postbuild`): [JENKINS-26918](https://issues.jenkins-ci.org/browse/JENKINS-26918)
- [ ] `Gradle` (`gradle`): [JENKINS-27393](https://issues.jenkins-ci.org/browse/JENKINS-27393)
- [ ] `CloverPublisher` (`clover`): [JENKINS-27302](https://issues.jenkins-ci.org/browse/JENKINS-27302)
- [ ] `MsBuildBuilder` (`msbuild`): [JENKINS-26948](https://issues.jenkins-ci.org/browse/JENKINS-26948)
- [ ] `HipChatNotifier` (`hipchat`): [JENKINS-27202](https://issues.jenkins-ci.org/browse/JENKINS-27202)
- [X] `LogParserPublisher` (`log-parser`): supported as of 2.0
- [ ] `SlackNotifier` (`slack`): [JENKINS-27652](https://issues.jenkins-ci.org/browse/JENKINS-27652)
- [ ] ~~`DescriptionSetterPublisher` (`description-setter`): [PR 7](https://github.com/jenkinsci/description-setter-plugin/pull/7)~~
- [ ] `CopyToSlaveBuildWrapper` and `CopyToMasterNotifier` (`copy-to-slave`): [JENKINS-28386](https://issues.jenkins-ci.org/browse/JENKINS-28386)
- [ ] `VeracodeNotifier` (`veracode-scanner`): [JENKINS-28387](https://issues.jenkins-ci.org/browse/JENKINS-28387)
- [X] `SeleniumHtmlReportPublisher` (`seleniumhtmlreport`): supported as of 1.0
- [ ] `GitPublisher` (`git`) or a custom step: [JENKINS-28335](https://issues.jenkins-ci.org/browse/JENKINS-28335)
- [ ] SonarQube Jenkins: [SONARJNKNS-213](http://jira.sonarsource.com/browse/SONARJNKNS-213)
- [ ] `VSphereBuildStepContainer` (`vsphere-cloud`): [JENKINS-28930](https://issues.jenkins-ci.org/browse/JENKINS-28930)
- [X] `ScoveragePublisher` (`scoverage`): supported as of 1.2.0
- [ ] `XShellBuilder` (`xshell`): [JENKINS-30372](https://issues.jenkins-ci.org/browse/JENKINS-30372)
- [ ] `AWSCodeDeployPublisher` (`codedeploy`): [issue 36](https://github.com/awslabs/aws-codedeploy-plugin/issues/36)

## Build wrappers

- [X] `ConfigFileBuildWrapper` (`config-file-provider`): supported as of 2.9.1
- [X] `Xvnc` (`xvnc`) supported as of 1.22
- [ ] `BuildUser` (`build-user-vars`): [JENKINS-26953](https://issues.jenkins-ci.org/browse/JENKINS-26953)
- [ ] `DashboardBuilder` (`environment-dashboard`): [issue 20](https://github.com/vipinsthename/environment-dashboard/issues/20)
- [X] `TimestamperBuildWrapper` (`timestamper`): supported as of 1.7
- [x] `MaskPasswordsBuildWrapper` (`mask-passwords`): supported as of 2.8
- [X] `XvfbBuildWrapper` (`xvfb`): supported as of 1.1.0-beta-1
- [X] `GCloudBuildWrapper` (`gcloud-sdk`): scheduled to be supported as of 0.0.2
- [X] `NpmPackagesBuildWrapper` (`nodejs`): scheduled to be supported as of 0.3
- [ ] `AnsiColorBuildWrapper` (`ansicolor`): scheduled to be supported as of 0.4.2
- [ ] `CustomToolInstallWrapper` (`custom-tools-plugin`): [JENKINS-30680](https://issues.jenkins-ci.org/browse/JENKINS-30680) 

## Triggers

- [X] `gerrit-trigger`: supported as of 2.15.0
- [ ] `ghprb`: [JENKINS-26591](https://issues.jenkins-ci.org/browse/JENKINS-26591)
- [X] `github`: supported as of 1.14.0
- [ ] `xtrigger-plugin`: [JENKINS-27301](https://issues.jenkins-ci.org/browse/JENKINS-27301)
- [ ] `deployment-notification`: [JENKINS-28632](https://issues.jenkins-ci.org/browse/JENKINS-28632)
- [X] `gitlab-plugin`: supported as of 1.1.26
- [X] `bitbucket`: supported as of 1.1.2

## Clouds

- [ ] `elasticbox`: [JENKINS-25978](https://issues.jenkins-ci.org/browse/JENKINS-25978) (could also include build wrapper integration)
- [ ] `mansion-cloud`: [JENKINS-24815](https://issues.jenkins-ci.org/browse/JENKINS-24815)
- [X] `mock-slave` (for prototyping): supported as of 1.7
- [X] `docker`: supported as of 0.8
- [X] `nectar-vmware` (CloudBees Jenkins Enterprise): supported as of 4.3.2
- [ ] `operations-center-cloud` (CloudBees Jenkins Enterprise/Operations Center): CJP-2951
- [X] `ec2`: known to work as is

## Miscellaneous

- [X] `rebuild`: supported as of 1.24
- [X] `parameterized-trigger` (to support a workflow as downstream): supported as of 2.28
- [X] `build-token-root`: supported as of 1.2
- [ ] `build-failure-analyzer`: [JENKINS-27123](https://issues.jenkins-ci.org/browse/JENKINS-27123)
- [ ] `shelve-project`: [JENKINS-26432](https://issues.jenkins-ci.org/browse/JENKINS-26432)
- [X] `job-dsl`: Workflow creation supported as of 1.29
- [X] `zentimestamp`: basic compatibility in 4.2
- [ ] `claim`: [JENKINS-27206](https://issues.jenkins-ci.org/browse/JENKINS-27206)
- [ ] `ListSubversionTagsParameterValue` (`subversion`): [JENKINS-27718](https://issues.jenkins-ci.org/browse/JENKINS-27718)
- [X] `authorize-project`: supported as of 1.1.0
- [ ] `lockable-resources` : [JENKINS-30269](https://issues.jenkins-ci.org/browse/JENKINS-30269)
- [X] `customize-build-now`: supported as of 1.1
- [ ] `test-results-analyzer` : [JENKINS-30522](https://issues.jenkins-ci.org/browse/JENKINS-30522)
- [ ] `embeddable-build-status`: [JENKINS-28642](https://issues.jenkins-ci.org/browse/JENKINS-28642)

## Custom steps

For cases when a first-class Workflow step (rather than an adaptation of functionality applicable to freestyle projects) makes sense.

- [X] `docker-workflow`: DSL based on `docker` global variable
- [X] `credentials-binding`: `withCredentials` step as of 1.3
- [X] `ssh-agent`: `sshagent` step as of 1.8
- [X] `parallel-test-executor`: `splitTests` step since 1.6
- [ ] `gerrit-trigger`: [JENKINS-26102](https://issues.jenkins-ci.org/browse/JENKINS-26102), [JENKINS-26103](https://issues.jenkins-ci.org/browse/JENKINS-26103)
- [X] `mailer`: `mail` step in Workflow 1.2
- [ ] `artifactory`: [JENKINS-30121](https://issues.jenkins-ci.org/browse/JENKINS-30121)

# Plugin Developer Guide

If you are maintaining (or creating) a plugin and wish its features to work smoothly with Workflow, there are a number of special considerations.

## Extension points accessible via metastep

Several common types of plugin features (`@Extension`s) can be invoked from a Workflow script without any special plugin dependencies so long as you use newer Jenkins core APIs.
Then there is “metastep” in Workflow (`step`, `checkout`, `wrap`) which loads the extension by class name and calls it.

### General guidelines

There are several considerations common to the various metasteps.

#### Jenkins core dependency

First, make sure the baseline Jenkins version in your POM is set to at least 1.568 (or 1.580.1, the next LTS).
This introduces some new API methods, and deprecates some old ones.

If you are nervous about making your plugin depend on a recent Jenkins version,
remember that you can always create a branch from your previous release (setting the version to `x.y.1-SNAPSHOT`) that works with older versions of Jenkins and `git cherry-pick -x` trunk changes into it as needed;
or merge from one branch to another if that is easier.
(`mvn -B release:prepare release:perform` works fine on a branch and knows to increment just the last version component.)

#### More general APIs

Replace `AbstractBuild.getProject` with `Run.getParent`.

`BuildListener` has also been replaced with `TaskListener` in new method overloads.

If you need a `Node` where the build is running to replace `getBuiltOn`, you can use `FilePath.getComputer`.

#### Constructor vs. setters

It is a good idea to replace a lengthy `@DataBoundConstructor` with a short one taking just truly mandatory parameters (such as a server location).
For all optional parameters, create a public setter marked with `@DataBoundSetter` (with any non-null default value set in the constructor or field initializer).
This allows most parameters to be left at their default values in a workflow script, not to mention simplifying ongoing code maintenance because it is much easier to introduce new options this way.

For Java-level compatibility, leave any previous constructors in place, but mark them `@Deprecated`.
Also remove `@DataBoundConstructor` from them (there can be only one).

##### Handling default values

To ensure _Snippet Generator_ enumerates only those options the user has actually customized from their form defaults, ensure that Jelly `default` attributes match the property defaults as seen from the getter.
For a cleaner XStream serial form in freestyle projects, it is best for the default value to also be represented as a null in the Java field.
So for example if you want a textual property which can sensibly default to blank, your configuration form would look like

```xml
<f:entry field="stuff" title="${%Stuff}">
    <f:textbox/>
</f:entry>
```

and your `Describable` should use

```java
private @CheckForNull String stuff;
public @CheckForNull String getStuff() {
    return stuff;
}
@DataBoundSetter public void setStuff(@CheckForNull String stuff) {
    this.stuff = Util.fixNull(stuff);
}
```

If you want a nonblank default, it is a little more complicated.
If you do not care about XStream hygiene, for example because the `Describable` is a Workflow `Step` (or is only being used as part of one):

```xml
<f:entry field="stuff" title="${%Stuff}">
    <f:textbox default="${descriptor.defaultStuff}"/>
</f:entry>
```

```java
private @Nonnull String stuff = DescriptorImpl.defaultStuff;
public @Nonnull String getStuff() {
    return stuff;
}
@DataBoundSetter public void setStuff(@Nonnull String stuff) {
    this.stuff = stuff;
}
@Extension public static class DescriptorImpl extends Descriptor<Whatever> {
    public static final String defaultStuff = "junk";
    // …
}
```

(The `Descriptor` is the most convenient place to put a constant for use from a Jelly view: `descriptor` is always defined even if `instance` is null, and Jelly/JEXL allows a `static` field to be loaded using instance-field notation.
From a Groovy view you could use any syntax supported by Java to refer to a constant, but Jelly in Jenkins is weaker: `getStatic` will not work on classes defined in plugins.)

To make sure the field is omitted from the XStream form when unmodified, you can use the same `Descriptor` and configuration form but null out the default:

```java
private @CheckForNull String stuff;
public @Nonnull String getStuff() {
    return stuff == null ? DescriptorImpl.defaultStuff : stuff;
}
@DataBoundSetter public void setStuff(@Nonnull String stuff) {
    this.stuff = stuff.equals(DescriptorImpl.defaultStuff) ? null : stuff;
}
```

None of these considerations apply to mandatory parameters with no default, which should be requested in the `@DataBoundConstructor` and have a simple getter.
(You could still have a `default` in the configuration form as a hint to new users, as a complement to a full description in `help-stuff.html`, but the value chosen will always be saved.)

### SCMs

See the [user documentation](scm-step/README.md) for background. The `checkout` metastep uses an `SCM`.

As the author of an SCM plugin, there are some changes you should make to ensure your plugin can be used from workflows.
You can use `mercurial-plugin` as a relatively straightforward code example.

#### Basic update

Check your plugin for compilation warnings relating to `hudson.scm.*` classes to see outstanding changes you need to make.
Most importantly, various methods in `SCM` which formerly took an `AbstractBuild` now take a more generic `Run` (i.e., potentially a workflow build) plus a `FilePath` (i.e., a workspace).
Use the specified workspace rather than the former `build.getWorkspace()`, which only worked for traditional projects with a single workspace.
Similarly, some methods formerly taking `AbstractProject` now take the more generic `Job`.
Be sure to use `@Override` wherever possible to make sure you are using the right overloads.

Note that `changelogFile` may now be null in `checkout`.
If so, just skip changelog generation.
`checkout` also now takes an `SCMRevisionState` so you can know what to compare against without referring back to the build.

`SCMDescriptor.isApplicable` should be switched to the `Job` overload.
Typically you will unconditionally return `true`.

#### Checkout key

You should override the new `getKey`.
This allows a workflow job to match up checkouts from build to build so it knows how to look for changes.

#### Browser selection

You may override the new `guessBrowser`, so that scripts do not need to specify the changelog browser to display.

#### Commit triggers

If you have a commit trigger, generally an `UnprotectedRootAction` which schedules builds, it will need a few changes.
Use `SCMTriggerItem` rather than the deprecated `SCMedItem`; use `SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem` rather than checking `instanceof`.
Its `getSCMs` method can be used to enumerate configured SCMs, which in the case of a workflow will be those run in the last build.
Use its `getSCMTrigger` method to look for a configured trigger (for example to check `isIgnorePostCommitHooks`).

Ideally you will already be integrated with the `scm-api` plugin and implementing `SCMSource`; if not, now is a good time to try it.
In the future workflows may take advantage of this API to support automatic creation of subprojects for each detected branch.

#### Explicit integration

If you want to provide a smoother experience for workflow users than is possible via the generic `scm` step,
you can add a (perhaps optional) dependency on `workflow-scm-step` to your plugin.
Define a `SCMStep` using `SCMStepDescriptor` and you can define a friendly, script-oriented syntax.
You still need to make the aforementioned changes, since at the end you are just preconfiguring an `SCM`.

### Build steps

See the [user documentation](basic-steps/CORE-STEPS.md) for background. The metastep is `step`.

To add support for use of a `Builder` or `Publisher` from a workflow, depend on Jenkins 1.577+, typically 1.580.1 ([tips](#basic-update)).
Then implement `SimpleBuildStep`, following the guidelines in [its Javadoc](http://javadoc.jenkins-ci.org/jenkins/tasks/SimpleBuildStep.html).
Also prefer `@DataBoundSetter`s to a sprawling `@DataBoundConstructor` ([tips](#constructor-vs-setters)).

Note that a `SimpleBuildStep` is designed to work also in a freestyle project, and thus assumes that a `FilePath workspace` is available (as well as some associated services, like a `Launcher`).
That is always true in a freestyle build, but is a potential limitation for use from a Workflow build.
For example, you might legitimately want to take some action outside the context of any workspace:

```groovy
node('win64') {
  bat 'make all'
  archive 'myapp.exe'
}
input 'Ready to tell the world?' // could pause indefinitely, do not tie up a slave
step([$class: 'FunkyNotificationBuilder', artifact: 'myapp.exe']) // ← FAILS!
```

Even if `FunkyNotificationBuilder` implements `SimpleBuildStep`, the above will fail, because the `workspace` required by `SimpleBuildStep.perform` is missing.
You could grab an arbitrary workspace just to run the builder:

```groovy
node('win64') {
  bat 'make all'
  archive 'myapp.exe'
}
input 'Ready to tell the world?'
node {
  step([$class: 'FunkyNotificationBuilder', artifact: 'myapp.exe']) // OK
}
```

but if the `workspace` is being ignored anyway (in this case because `FunkyNotificationBuilder` only cares about artifacts that have already been archived), it may be better to just write a custom step (described below).

### Build wrappers

Here the metastep is `wrap`.
To add support for a `BuildWrapper`, depend on Jenkins 1.599+ (typically 1.609.1), and implement `SimpleBuildWrapper`, following the guidelines in [its Javadoc](http://javadoc.jenkins-ci.org/jenkins/tasks/SimpleBuildWrapper.html).

Like `SimpleBuildStep`, wrappers written this way always require a workspace.
If that would be constricting, consider writing a custom step instead.

## Triggers

Replace `Trigger<AbstractProject>` with `Trigger<X>` where `X` is `Job` or perhaps `ParameterizedJob` or `SCMTriggerItem` and implement `TriggerDescriptor.isApplicable` accordingly.

## Clouds

Do not necessarily need any special integration, but are encouraged to use `OnceRetentionStrategy` from `durable-task` to allow flow builds to survive restarts.

## Custom steps

Plugins can also implement custom Workflow steps with specialized behavior.
See [here](step-api/README.md) for more.

## Historical background

Traditional Jenkins `Job`s are defined in a fairly deep type hierarchy: `FreestyleProject` → `Project` → `AbstractProject` → `Job` → `AbstractItem` → `Item`.
(As well as paired `Run` types: `FreestyleBuild`, etc.)
In older versions of Jenkins, much of the interesting implementation was in `AbstractProject` (or `AbstractBuild`), which was packed full of assorted features not present in `Job` (or `Run`).
Some of these features were also needed by Workflow, like having a programmatic way to start a build (optionally with parameters), or lazy-load build records, or integrate with SCM triggers.
Others were not applicable to Workflow, like declaring a single SCM and a single workspace per build, or being tied to a specific label, or running a linear sequence of build steps within the scope of a single Java method call.

`WorkflowJob` directly extends `Job` since it cannot act like an `AbstractProject`.
Therefore some refactoring was needed, to make the relevant features available to other `Job` types without code or API duplication.
Rather than introduce yet another level into the type hierarchy (and freezing for all time the decision about which features are more “generic” than others), mixins were introduced.
Each encapsulates a set of related functionality originally tied to `AbstractProject` but now also usable from `WorkflowJob` (and potentially other future `Job` types).

* `ParameterizedJobMixIn` allows a job to be scheduled to the queue (the older `BuildableItem` was inadequate), taking care also of build parameters and the REST build trigger.
* `SCMTriggerItem` integrates with `SCMTrigger`, including a definition of which SCM or SCMs a job is using, and how it should perform polling. It also allows various plugins to interoperate with the Multiple SCMs plugin without needing an explicit dependency. Supersedes and deprecates `SCMedItem`.
* `LazyBuildMixIn` handles the plumbing of lazy-loading build records (a system introduced in Jenkins 1.485).

For Workflow compatibility, plugins formerly referring to `AbstractProject`/`AbstractBuild` will generally need to start dealing with `Job`/`Run` but may also need to refer to `ParameterizedJobMixIn` and/or ``SCMTriggerItem`.
(`LazyBuildMixIn` is rarely needed from outside code, as the methods defined in `Job`/`Run` suffice for typical purposes.)

Future improvements to Workflow may well require yet more implementation code to be extracted from `AbstractProject`/`AbstractBuild`.
The main constraint is the need to retain binary compatibility.
