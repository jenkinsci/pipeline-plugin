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

Having the shared library script in Git allows you to track changes, perform
tested deployments, and reuse the same scripts across a large number of instances.

Note that the repository is initially empty of any commits, so it is possible to push an existing repository here.
Normally you would instead `clone` it to get started, in which case Git will note

    warning: remote HEAD refers to nonexistent ref, unable to checkout.

To set things up after cloning, start with:

    git checkout -b master

Now you may add and commit files normally.
For your first push to Jenkins you will need to set up a tracking branch:

    git push --set-upstream origin master

Thereafter it should suffice to run:

    git push

### Writing shared code
At the base level, any valid Groovy code is OK. So you can define data structures,
utility functions, and etc., like this:

    $ cat src/org/foo/Point.groovy
    package org.foo;

    // point in 3D space
    class Point {
      float x,y,z;
    }

However classes written like this cannot call step functions like `sh` or `git`.
More often than not, what you want to define is a series of functions that in turn invoke
other workflow step functions. You can do this by not explicitly defining the enclosing class,
just like your main workflow script itself:

    $ cat src/org/foo/Zot.groovy
    package org.foo;

    def checkOutFrom(repo) {
      git url: "git@github.com:jenkinsci/${repo}"
    }

You can then call such function from your main workflow script like this:

    def z = new org.foo.Zot()
    z.checkOutFrom(repo)

