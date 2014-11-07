# Workflow Global Library

When you have multiple workflow jobs, you often want to share some parts of the workflow
scripts between them to keep workflow scripts [DRY](http://en.wikipedia.org/wiki/Don't_repeat_yourself).
A very common use case is that you have many projects that are built in the similar way.

This plugin adds that functionality by creating a "shared library script" Git repository inside Jenkins.
Every workflow script in your Jenkins see these shared library scripts in their classpath.


### Directory structure

The directory structure of the shared library repository is as follows:

    (root)
     +- src                 # groovy source files
         +- org
             +- foo
                 +- Bar.groovy  # for org.foo.Bar class


The `src` directory should look like standard Java source directory structure.
This directory is added to the classpath when executing workflows. The groovy
source files in this directory get the same sandbox / CPS transformation
just like your workflow scripts.

Directories other than "src" is reserved for future enhancements.


### Accessing repository
This directory is managed by Git, and you'll deploy new changes through `git push`.
The repository is exposed in two endpoints:

 * `http://server/jenkins/workflowLibs.git` (when your Jenkins is `http://server/jenkins/`.
 * `ssh://USERNAME@server:PORT/workflowLibs.git` through [Jenkins SSH](https://wiki.jenkins-ci.org/display/JENKINS/Jenkins+SSH)
