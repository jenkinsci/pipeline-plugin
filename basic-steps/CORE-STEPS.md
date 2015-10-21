# Introduction

Many Jenkins plugins add builders or post-build actions (collectively, _build steps_) for use in freestyle and similar projects.
(Jenkins core also adds a few of these, though most have been split off into their own plugins or could be split off.)

In many cases these build steps would be valuable to use from workflows, but it would be overkill to define a separate Workflow-only step.
Therefore selected build steps can be called directly from workflows.

# Syntax

As an example, you can write a flow:

```groovy
node {
    sh 'make something'
    step([$class: 'ArtifactArchiver', artifacts: 'something'])
}
```

Here we are running the standard _Archive the artifacts_ post-build action (`hudson.tasks.ArtifactArchiver`),
and configuring the _Files to archive_ property (`artifacts`) to archive our file `something` produced in an earlier step.
The easiest way to see what class and field names to use is to use the _Snippet Generator_ feature in the workflow configuration page.

See the [compatibility list](../COMPATIBILITY.md) for the list of currently supported steps.

# Interacting with build status

Builders generally have a simple mode of operation: they run, and either pass or fail.
So you can call these at any point in your flow.

Post-build actions (also known as _publishers_) are divided into two classes:

* _Recorders_ like the JUnit publisher add something to the build, and might affect its status.
* _Notifiers_ like the mailer cannot affect the build’s status, though they may behave differently depending on its status.

When a recorder is run from a flow, it might set the build’s status (for example to unstable), but otherwise is likely to work intuitively.
Running a notifier is trickier since normally a flow in progress has no status yet, unlike a freestyle project whose status is determined before the notifier is called.
To help interoperate better with these, you can use the `catchError` step, or manually set a build status using `currentBuild.result`.
See the help for the `catchError` step for examples.

## Plain catch blocks

Some important publishers also have dedicated Workflow steps, so that you can use a more flexible idiom.
For example, `mail` lets you unconditionally send mail of your choice:

```groovy
node {
    try {
        sh 'might fail'
        mail subject: 'all well', to: 'admin@somewhere', body: 'All well.'
    } catch (e) {
        def w = new StringWriter()
        e.printStackTrace(new PrintWriter(w))
        mail subject: "failed with ${e.message}", to: 'admin@somewhere', body: "Failed: ${w}"
        throw e
    }
}
```

though this would not automatically adjust the message according to the status of _previous_ builds as the standard mail notifier does.
For that, check if `currentBuild.previousBuild` exists, what its `.result` is, etc.

# Build wrappers

The `wrap` step may be used to run a build wrapper defined originally for freestyle projects.
In a workflow, any block of code (inside `node`) may be wrapped in this way, not necessarily the whole build.

For example, the Xvnc plugin allows a headless build server to run GUI tests by allocating an in-memory-only X11 display.
To use this plugin from a workflow, assuming a version with the appropriate update:

```groovy
node('linux') {
  wrap([$class: 'Xvnc', useXauthority: true]) {
    // here $DISPLAY is set to :11 or similar, and $XAUTHORITY too
    sh 'make selenium-tests' // or whatever
  }
  // now the display is torn down and the environment variables gone
}
```

# Adding support from plugins

See the [compatibility guide](../COMPATIBILITY.md#plugin-developer-guide).
