# Introduction

This plugin allows workflows to use standard Jenkins SCM plugins to check out source code.
The goals are the maximum possible compatibility with existing plugins, and great flexibility for script authors.

# Features

A freestyle project has a single SCM configured in the UI that governs the one and only workspace for the build.
A workflow can be configured similarly, but the SCM definition becomes a regular step in its script.
In the simplest case you would just do an SCM clone/update at the start of your script, as soon as you have allocated a slave with a workspace:

```
node {
    git url: 'https://github.com/user/repo'
    sh 'make all'
}
```

Jenkins will clone the repository into the workspace and continue with your script.
(Subsequent builds will update rather than clone, if the same slave and workspace are available again.)

## Multiple SCMs

While freestyle projects can use the Multiple SCMs plugin to check out more than one repository,
or specify multiple locations in SCM plugins that support that (notably the Git plugin),
this support is quite limited.
In a workflow you can check out multiple SCMs, of the same or different kinds, in the same or different workspaces, wherever and whenever you like.
For example, to check out and build several repositories in parallel, each on its own slave:

```
parallel repos.collectEntries {repo -> [/* thread label */repo, {
    node {
        dir('sources') { // switch to subdir
            git url: "https://github.com/user/${repo}"
            sh 'make all -Dtarget=../build'
        }
    }
}]}
```

## Changelogs

By default each build will show changes from the previous build in its changelog as usual, and you can see an overall changelog on the project index page.
You may specify `changelog: false` to disable changelog generation if it is not of interest, or too large to compute efficiently.

Jenkins will automatically remember the SCMs run in the _last build_ of the project and compute changes accordingly.
This means that you can run multiple SCMs, even from a dynamic list, and get a reasonable changelog.
(At least for those checkouts that remain constant from build to build, as identified by a key defined by the SCM plugin, typically based on something like the repository location and branch.)

## Polling

If you configure the _Poll SCM_ trigger in the workflow’s UI configuration screen, then by default Jenkins will also poll for changes according to the selected _Schedule_, and schedule new builds automatically if changes are detected.
(Note that this configuration is not part of the flow script, because it affects activities that Jenkins runs outside of the flow.)
Some SCMs allow polling with no workspace, which is ideal; others will try to lock the same slave and workspace previously used, to run polling on the slave.

To avoid polling the server repeatedly, most SCM plugins allow remote commit triggers, such as the `/git/notifyCommit?url=…` HTTP endpoint in the case of the Git plugin.
These also work with workflows, unless (as with freestyle projects) you checked _Ignore post-commit hooks_ in a _Poll SCM_ block.
Depending on the SCM plugin, you may still need to configure a _Poll SCM_ trigger, though its _Schedule_ could be empty (or `@daily`, to serve as a fallback in case the commit triggers fail).

Polling is supported across multiple SCMs (changes in one or more will trigger a new build), and again is done according to the SCMs used in the last build of the workflow.

You may specify `poll: false` to disable polling for an SCM checkout.

# Built-in integrations

Currently there are special integrations with the Git (`git` step) and Subversion (`svn` step) plugins.
At the moment these are very simple and take just a `url` parameter.
Richer configuration may come in the future.

# Generic SCM step

The `checkout` step may be used to run any other SCM plugin, provided that it has been brought up to date as described below.
It could also be used to run an SCM for which there is a special integration that lacks support for an exotic feature.
The step takes an `scm` parameter which is a map containing at least a `$class` parameter giving the full or simple class name of the desired `SCM` implementation, and the standard `poll` and `changelog` parameters.

It also takes any other parameters supported by the SCM plugin in its configuration form, using their internal names and values, which you can see examples of in the `config.xml` of a freestyle project.
Optional parameters can be omitted and will take their default values.
For example, to run Mercurial (1.51-beta-2 or higher):

    checkout scm: [$class: 'MercurialSCM', source: 'ssh://hg@bitbucket.org/user/repo', clean: true, credentialsId: '1234-5678-abcd'], poll: false

Note that if `scm` is the only parameter, you can omit its name as usual, but Groovy syntax then requires parentheses around the value:

    checkout([$class: 'MercurialSCM', source: 'ssh://hg@bitbucket.org/user/repo'])

Here `source` is a mandatory parameter (_Repository URL_ in the UI), and `clean` (_Clean Build_) and `credentialsId` (_Credentials_) are optional parameters.
This would correspond roughly to a freestyle project configured as follows:

```
  <scm class="hudson.plugins.mercurial.MercurialSCM">
    <installation>(Default)</installation>
    <source>ssh://hg@bitbucket.org/user/repo</source>
    <modules></modules>
    <revisionType>BRANCH</revisionType>
    <revision>default</revision>
    <clean>true</clean>
    <credentialsId>1234-5678-abcd</credentialsId>
    <disableChangeLog>false</disableChangeLog>
  </scm>
```

with no `<hudson.triggers.SCMTrigger>` (polling).

# Supporting Workflow from an SCM plugin

As the author of an SCM plugin, there are some changes you should make to ensure your plugin can be used from workflows.
You can use `mercurial-plugin` as a relatively straightforward code example.

## Basic update

First, make sure the baseline Jenkins version in your POM is set to at least 1.568 (or 1.580.1, the next LTS).
This introduces some new API methods, and deprecates some old ones.

If you are nervous about making your plugin depend on a recent Jenkins version,
remember that you can always create a branch from your previous release (setting the version to `x.y.1-SNAPSHOT`) that works with older versions of Jenkins and `git cherry-pick -x` trunk changes into it as needed;
or merge from one branch to another if that is easier.
(`mvn -B release:prepare release:perform` works fine on a branch and knows to increment just the last version component.)

Check your plugin for compilation warnings relating to `hudson.scm.*` classes to see outstanding changes you need to make.
Most importantly, various methods in `SCM` which formerly took an `AbstractBuild` now take a more generic `Run` (i.e., potentially a workflow build) plus a `FilePath` (i.e., a workspace).
Use the specified workspace rather than the former `build.getWorkspace()`, which only worked for traditional projects with a single workspace.
Similarly, some methods formerly taking `AbstractProject` now take the more generic `Job`.
Be sure to use `@Override` wherever possible to make sure you are using the right overloads.

`BuildListener` has also been replaced with `TaskListener` in new method overloads.

Note that `changelogFile` may now be null in `checkout`.
If so, just skip changelog generation.
`checkout` also now takes an `SCMRevisionState` so you can know what to compare against without referring back to the build.

If you need a `Node` where the build is running to replace `getBuiltOn`, you can find one from the `FilePath`, though currently it is cumbersome;
a convenience API for this is [expected to be defined soon](https://trello.com/c/doFFMdUm/46-filepath-getcomputer).

`SCMDescriptor.isApplicable` should be switched to the `Job` overload.
Typically you will unconditionally return `true`.

## Checkout key

You should override the new `getKey`.
This allows a workflow job to match up checkouts from build to build so it knows how to look for changes.

## Browser selection

You may override the new `guessBrowser`, so that scripts do not need to specify the changelog browser to display.

## Commit triggers

If you have a commit trigger, generally an `UnprotectedRootAction` which schedules builds, it will need a few changes.
Use `SCMTriggerItem` rather than the deprecated `SCMedItem`; use `SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem` rather than checking `instanceof`.
Its `getSCMs` method can be used to enumerate configured SCMs, which in the case of a workflow will be those run in the last build.
Use its `getSCMTrigger` method to look for a configured trigger (for example to check `isIgnorePostCommitHooks`).

Ideally you will already be integrated with the `scm-api` plugin and implementing `SCMSource`; if not, now is a good time to try it.
In the future workflows may take advantage of this API to support automatic creation of subprojects for each detected branch.

## Constructor vs. setters

It is a good idea to replace a lengthy `@DataBoundConstructor` with a short one taking just truly mandatory parameters (such as a server location).
For all optional parameters, create a public setter marked with `@DataBoundSetter` (with any non-null default value set in the constructor or field initializer).
This allows most parameters to be left at their default values in a workflow script, not to mention simplifying ongoing code maintenance because it is much easier to introduce new options this way.

For Java-level compatibility, leave any previous constructors in place, but mark them `@Deprecated`.
Also remove `@DataBoundConstructor` from them (there can be only one).

## Explicit integration

If you want to provide a smoother experience for workflow users than is possible via the generic `scm` step,
you can add a (perhaps optional) dependency on `workflow-scm-step` to your plugin.
Define a `SCMStep` using `SCMStepDescriptor` and you can define a friendly, script-oriented syntax.
You still need to make the aforementioned changes, since at the end you are just preconfiguring an `SCM`.
