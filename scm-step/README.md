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

## git

the `git` step lets you define a repository to clone. For authentication, a reference to a configured credential can
be passed.

```groovy
git url: 'git@git.local.domain:ps/project.git', credentialsId: '67c3072d-b9a7-44fa-a5aa-560ba9c1662f'
```

## svn

At the moment this step is very simple and takes just a `url` parameter.
Richer configuration may come in the future.

# Generic SCM step

The `checkout` step may be used to run any other SCM plugin, provided that it has been brought up to date as described below.
(See the [compatibility list](../COMPATIBILITY.md) for the list of currently SCMs.)
It could also be used to run an SCM for which there is a special integration that lacks support for an exotic feature.
The step takes an `scm` parameter which is a map containing at least a `$class` parameter giving the full or simple class name of the desired `SCM` implementation, and the standard `poll` and `changelog` parameters.

It also takes any other parameters supported by the SCM plugin in its configuration form, using their internal names and values; use _Snippet Generator_ to get a detailed example for your SCM.
Optional parameters can be omitted and will take their default values (to the extent supported by the SCM plugin).
For example, to run Mercurial (1.51-beta-2 or higher):

    checkout scm: [$class: 'MercurialSCM', source: 'ssh://hg@bitbucket.org/user/repo', clean: true, credentialsId: '1234-5678-abcd'], poll: false

Note that if `scm` is the only parameter, you can omit its name as usual, but Groovy syntax then requires parentheses around the value:

    checkout([$class: 'MercurialSCM', source: 'ssh://hg@bitbucket.org/user/repo'])

Here `source` is a mandatory parameter (_Repository URL_ in the UI), and `clean` (_Clean Build_) and `credentialsId` (_Credentials_) are optional parameters.
This would correspond roughly to a freestyle project whose `config.xml` includes:

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

See the [compatibility guide](../COMPATIBILITY.md#plugin-developer-guide).
