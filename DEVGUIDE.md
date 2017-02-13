# Plugin Developer Guide

If you are maintaining (or creating) a plugin and wish its features to work smoothly with Pipeline, there are a number of special considerations.

## Extension points accessible via metastep

Several common types of plugin features (`@Extension`s) can be invoked from a Pipeline script without any special plugin dependencies so long as you use newer Jenkins core APIs.
Then there is “metastep” in Pipeline (`step`, `checkout`, `wrap`) which loads the extension by class name and calls it.

### General guidelines

There are several considerations common to the various metasteps.

#### Jenkins core dependency

First, make sure the baseline Jenkins version in your `pom.xml` is sufficiently new.

Suggested versions for:
- [Basic usage](#user-content-basic-update)
- [Build wrappers](#user-content-build-wrappers-1)

This introduces some new API methods, and deprecates some old ones.

If you are nervous about making your plugin depend on a recent Jenkins version,
remember that you can always create a branch from your previous release (setting the version to `x.y.1-SNAPSHOT`) that works with older versions of Jenkins and `git cherry-pick -x` trunk changes into it as needed;
or merge from one branch to another if that is easier.
(`mvn -B release:prepare release:perform` works fine on a branch and knows to increment just the last version component.)

#### More general APIs

Replace `AbstractBuild.getProject` with `Run.getParent`.

`BuildListener` has also been replaced with `TaskListener` in new method overloads.

If you need a `Node` where the build is running to replace `getBuiltOn`, you can use `FilePath.toComputer`.

`TransientProjectActionFactory` can be replaced by `TransientActionFactory<Job>`.

#### Variable substitutions

There is no equivalent to `AbstractBuild.getBuildVariables()` for `WorkflowRun` (any Groovy local variables are not accessible as such).
Also, `WorkflowRun.getEnvironment(TaskListener)` _is_ implemented, but only yields the initial build environment, irrespective of `withEnv` blocks and the like.
(To get the _contextual_ environment in a `Step`, you can inject `EnvVars` using `@StepContextParameter`;
pending [JENKINS-29144](https://issues.jenkins-ci.org/browse/JENKINS-29144) there is no equivalent for a `SimpleBuildStep`.
A `SimpleBuildWrapper` does have access to an `initialEnvironment` if required.)

Anyway code run from Pipeline should take any configuration values as literal strings and make no attempt to perform variable substitution (including via the `token-macro` plugin),
since the script author would be using Groovy facilities (`"like ${this}"`) for any desired dynamic behavior.
To have a single code fragment support both Pipeline and traditional builds, you can use idioms such as:

```java
private final String location;
public String getLocation() {
    return location;
}
@DataBoundSetter public void setLocation(String location) {
    this.location = location;
}
private String actualLocation(Run<?,?> build, TaskListener listener) {
    if (build instanceof AbstractBuild) {
        EnvVars env = build.getEnvironment(listener);
        env.overrideAll(((AbstractBuild) build).getBuildVariables());
        return env.expand(location);
    } else {
        return location;
    }
}
```

[JENKINS-35671](https://issues.jenkins-ci.org/browse/JENKINS-35671) would simplify this.

#### Constructor vs. setters

It is a good idea to replace a lengthy `@DataBoundConstructor` with a short one taking just truly mandatory parameters (such as a server location).
For all optional parameters, create a public setter marked with `@DataBoundSetter` (with any non-null default value set in the constructor or field initializer).
This allows most parameters to be left at their default values in a Pipeline script, not to mention simplifying ongoing code maintenance because it is much easier to introduce new options this way.

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
If you do not care about XStream hygiene, for example because the `Describable` is a Pipeline `Step` (or is only being used as part of one):

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

#### Handling secrets

If your plugin ever stored secrets (such as passwords) in a plain `String`-valued fields, it was already insecure and should at least have been using `Secret`.
`Secret`-valued fields are more secure, but are not really appropriate for projects defined in source code, like Pipeline jobs.

Instead you should integrate with the [Credentials plugin](https://wiki.jenkins-ci.org/display/JENKINS/Credentials+Plugin). Then your builder etc. would typically have a `credentialsId` field which just refers to the ID of the credentials.
(The user can pick a mnemonic ID for use in scripted jobs.)
Typically the `config.jelly` used in _Snippet Generator_ will have a `<c:select/>` control,
backed by a `doFillCredentialsId` web method on the `Descriptor` to enumerate credentials currently available of the intended type (such as `StandardUsernamePasswordCredentials`) and perhaps restricted to some domain (such as a hostname obtained via a `@QueryParameter` from a nearby form field).
At runtime, you will look up the credentials by ID and use them.

Plugins formerly using `Secret` will generally need to use an `@Initializer` to migrate the configuration of freestyle projects to use Credentials instead.

The details of adopting Credentials are too numerous to list here.
Pending a proper developer’s guide, it is best to follow the example of well-maintained plugins which have already made such a conversion.

#### Defining symbols

By default, scripts making use of your plugin will need to refer to the (simple) Java class name of the extension.
For example, if you defined

```java
public class ForgetBuilder extends Builder implements SimpleBuildStep {
    private final String what;
    @DataBoundConstructor public ForgetBuilder(String what) {this.what = what;}
    public String getWhat() {return what;}
    @Override public void perform(Run build, FilePath workspace, Launcher launcher,
            TaskListener listener) throws InterruptedException, IOException {
        listener.getLogger().println("What was " + what + "?");
    }
    @Extension public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override public String getDisplayName() {return "Forget things";}
        @Override public boolean isApplicable(Class<? extends AbstractProject> t) {return true;}
    }
}
```

then scripts would use this builder as follows:

```groovy
step([$class: 'ForgetBuilder', what: 'everything'])
```

To make for a more attractive and mnemonic usage style, you can depend on `org.jenkins-ci.plugins:structs`
and add a `@Symbol` to your `Descriptor`, uniquely identifying it among extensions of its kind
(in this example, `SimpleBuildStep`s):

```java
// …
@Symbol("forget")
@Extension public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
// …
```

Now when users of sufficiently new versions of Pipeline wish to run your builder, they can use a shorter syntax:

```groovy
forget 'everything'
```

`@Symbol`s are not limited to extensions used at “top level” by metasteps such as `step`.
Any `Descriptor` can have an associated symbol.
Therefore if your plugin uses other `Describable`s for any kind of structured configuration,
you should also annotate those implementations.
For example if you have defined an extension point

```java
public abstract Timeframe extends AbstractDescribableImpl<Timeframe> implements ExtensionPoint {
    public abstract boolean areWeThereYet();
}
```

with some implementations such as

```java
@Extension public class Immediately extends Timeframe {
    @DataBoundConstructor public Immediately() {}
    @Override public boolean areWeThereYet() {return true;}
    @Symbol("now")
    @Extension public static DescriptorImpl extends Descriptor<Timeframe> {
        @Override public String getDisplayName() {return "Right now";}
    }
}
```

or

```java
@Extension public class HoursAway extends Timeframe {
    private final long hours;
    @DataBoundConstructor public HoursAway(long hours) {this.hours = hours;}
    public long getHours() {return hours;}
    @Override public boolean areWeThereYet() {/* … */}
    @Symbol("soon")
    @Extension public static DescriptorImpl extends Descriptor<Timeframe> {
        @Override public String getDisplayName() {return "Pretty soon";}
    }
}
```

which are selectable in your configuration

```java
private Timeframe when = new Immediately();
public Timeframe getWhen() {return when;}
@DataBoundSetter public void setWhen(Timeframe when) {this.when = when;}
```

then a script could select a timeframe using the symbols you have defined:

```groovy
forget 'nothing' // whenever
forget what: 'something', when: now()
forget what: 'everything else', when: soon(1)
```

_Snippet Generator_ will offer the simplified syntax wherever available.
Freestyle project configuration will ignore the symbol, though a future version of the Job DSL plugin may take advantage of it.

### SCMs

See the [user documentation](https://github.com/jenkinsci/workflow-scm-step-plugin/blob/master/README.md) for background. The `checkout` metastep uses an `SCM`.

As the author of an SCM plugin, there are some changes you should make to ensure your plugin can be used from pipelines.
You can use `mercurial-plugin` as a relatively straightforward code example.

#### Basic update

Make sure your Jenkins baseline is at least 1.568 (or 1.580.1, the next LTS).
Check your plugin for compilation warnings relating to `hudson.scm.*` classes to see outstanding changes you need to make.
Most importantly, various methods in `SCM` which formerly took an `AbstractBuild` now take a more generic `Run` (i.e., potentially a Pipeline build) plus a `FilePath` (i.e., a workspace).
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
This allows a Pipeline job to match up checkouts from build to build so it knows how to look for changes.

#### Browser selection

You may override the new `guessBrowser`, so that scripts do not need to specify the changelog browser to display.

#### Commit triggers

If you have a commit trigger, generally an `UnprotectedRootAction` which schedules builds, it will need a few changes.
Use `SCMTriggerItem` rather than the deprecated `SCMedItem`; use `SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem` rather than checking `instanceof`.
Its `getSCMs` method can be used to enumerate configured SCMs, which in the case of a pipeline will be those run in the last build.
Use its `getSCMTrigger` method to look for a configured trigger (for example to check `isIgnorePostCommitHooks`).

Ideally you will already be integrated with the `scm-api` plugin and implementing `SCMSource`; if not, now is a good time to try it.
In the future pipelines may take advantage of this API to support automatic creation of subprojects for each detected branch.

#### Explicit integration

If you want to provide a smoother experience for Pipeline users than is possible via the generic `scm` step,
you can add a (perhaps optional) dependency on `workflow-scm-step` to your plugin.
Define a `SCMStep` using `SCMStepDescriptor` and you can define a friendly, script-oriented syntax.
You still need to make the aforementioned changes, since at the end you are just preconfiguring an `SCM`.

### Build steps

See the [user documentation](https://github.com/jenkinsci/workflow-basic-steps-plugin/blob/master/CORE-STEPS.md) for background. The metastep is `step`.

To add support for use of a `Builder` or `Publisher` from a pipeline, depend on Jenkins 1.577+, typically 1.580.1 ([tips](#basic-update)).
Then implement `SimpleBuildStep`, following the guidelines in [its Javadoc](http://javadoc.jenkins-ci.org/jenkins/tasks/SimpleBuildStep.html).
Also prefer `@DataBoundSetter`s to a sprawling `@DataBoundConstructor` ([tips](#constructor-vs-setters)).

#### Mandatory workspace context

Note that a `SimpleBuildStep` is designed to work also in a freestyle project, and thus assumes that a `FilePath workspace` is available (as well as some associated services, like a `Launcher`).
That is always true in a freestyle build, but is a potential limitation for use from a Pipeline build.
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

#### Run listeners vs. publishers

For code which genuinely has to run after the build completes, there is `RunListener`.
If the behavior of this hook needs to be customizable at the job level, the usual technique would be to define a `JobProperty`.
(One distinction from freestyle projects is that in the case of Pipeline there is no way to introspect the “list of build steps” or “list of publishers” or “list of build wrappers” so any decisions based on such metadata are impossible.)

In most other cases, you just want some code to run after some _portion_ of the build completes, which is typically handled with a `Publisher` if you wish to share a code base with freestyle projects.
For regular `Publisher`s, which are run as part of the build, a Pipeline script would use the `step` metastep. There are two subtypes:
* `Recorder`s generally should be placed inline with other build steps in whatever order makes sense.
* `Notifier`s can be placed in a `finally` block, or you can use the `catchError` step. [This document](https://github.com/jenkinsci/workflow-basic-steps-plugin/blob/master/CORE-STEPS.md#interacting-with-build-status) goes into depth.

### Build wrappers

Here the metastep is `wrap`.
To add support for a `BuildWrapper`, depend on Jenkins 1.599+ (typically 1.609.1), and implement `SimpleBuildWrapper`, following the guidelines in [its Javadoc](http://javadoc.jenkins-ci.org/jenkins/tasks/SimpleBuildWrapper.html).

Like `SimpleBuildStep`, wrappers written this way always require a workspace.
If that would be constricting, consider writing a custom step instead.

## Triggers

Replace `Trigger<AbstractProject>` with `Trigger<X>` where `X` is `Job` or perhaps `ParameterizedJob` or `SCMTriggerItem` and implement `TriggerDescriptor.isApplicable` accordingly.

Use `EnvironmentContributor` rather than `RunListener.setUpEnvironment`.

## Clouds

Do not necessarily need any special integration, but are encouraged to use `OnceRetentionStrategy` from `durable-task` to allow Pipeline builds to survive restarts.

## Custom steps

Plugins can also implement custom Pipeline steps with specialized behavior.
See [here](https://github.com/jenkinsci/workflow-step-api-plugin/blob/master/README.md) for more.

## Historical background

Traditional Jenkins `Job`s are defined in a fairly deep type hierarchy: `FreestyleProject` → `Project` → `AbstractProject` → `Job` → `AbstractItem` → `Item`.
(As well as paired `Run` types: `FreestyleBuild`, etc.)
In older versions of Jenkins, much of the interesting implementation was in `AbstractProject` (or `AbstractBuild`), which was packed full of assorted features not present in `Job` (or `Run`).
Some of these features were also needed by Pipeline, like having a programmatic way to start a build (optionally with parameters), or lazy-load build records, or integrate with SCM triggers.
Others were not applicable to Pipeline, like declaring a single SCM and a single workspace per build, or being tied to a specific label, or running a linear sequence of build steps within the scope of a single Java method call, or having a simple list of build steps and wrappers whose configuration is guaranteed to remain the same from build to build.

`WorkflowJob` directly extends `Job` since it cannot act like an `AbstractProject`.
Therefore some refactoring was needed, to make the relevant features available to other `Job` types without code or API duplication.
Rather than introduce yet another level into the type hierarchy (and freezing for all time the decision about which features are more “generic” than others), mixins were introduced.
Each encapsulates a set of related functionality originally tied to `AbstractProject` but now also usable from `WorkflowJob` (and potentially other future `Job` types).

* `ParameterizedJobMixIn` allows a job to be scheduled to the queue (the older `BuildableItem` was inadequate), taking care also of build parameters and the REST build trigger.
* `SCMTriggerItem` integrates with `SCMTrigger`, including a definition of which SCM or SCMs a job is using, and how it should perform polling. It also allows various plugins to interoperate with the Multiple SCMs plugin without needing an explicit dependency. Supersedes and deprecates `SCMedItem`.
* `LazyBuildMixIn` handles the plumbing of lazy-loading build records (a system introduced in Jenkins 1.485).

For Pipeline compatibility, plugins formerly referring to `AbstractProject`/`AbstractBuild` will generally need to start dealing with `Job`/`Run` but may also need to refer to `ParameterizedJobMixIn` and/or `SCMTriggerItem`.
(`LazyBuildMixIn` is rarely needed from outside code, as the methods defined in `Job`/`Run` suffice for typical purposes.)

Future improvements to Pipeline may well require yet more implementation code to be extracted from `AbstractProject`/`AbstractBuild`.
The main constraint is the need to retain binary compatibility.
