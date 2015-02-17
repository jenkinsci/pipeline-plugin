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
To help interoperate better with these, you can use the `catchError` step:

```groovy
node {
    catchError {
        sh 'might fail'
    }
    step([$class: 'Mailer', recipients: 'admin@somewhere'])
}
```

If its body fails, the flow build’s status will be set to failed, so that subsequent notifier steps will see that this build is failed.
In the case of the mail sender, this means that it will send mail.
(It may also send mail if this build _succeeded_ but previous ones failed, and so on.)

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
        e.printStackTrace(w)
        mail subject: "failed with ${e.message}", to: 'admin@somewhere', body: "Failed: ${w}"
        throw e
    }
}
```

though this would not automatically adjust the message according to the status of _previous_ builds as the standard mail notifier does.
That would be possible only via [JENKINS-26834](https://issues.jenkins-ci.org/browse/JENKINS-26834).

# Adding support from plugins

As a plugin author, to add support for use of your build step from a workflow, depend on Jenkins 1.577+, typically 1.580.1 ([tips](../scm-step/README.md#basic-update)).
Then implement `SimpleBuildStep`, following the guidelines in [its Javadoc](http://javadoc.jenkins-ci.org/jenkins/tasks/SimpleBuildStep.html).
Also prefer `@DataBoundSetter`s to a sprawling `@DataBoundConstructor` ([tips](../scm-step/README.md#constructor-vs-setters)).
