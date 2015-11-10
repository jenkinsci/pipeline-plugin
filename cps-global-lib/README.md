# Workflow Global Library

When you have multiple workflow jobs, you often want to share some parts of the workflow
scripts between them to keep workflow scripts [DRY](http://en.wikipedia.org/wiki/Don't_repeat_yourself).
A very common use case is that you have many projects that are built in the similar way.

This plugin adds that functionality by creating a "shared library script" Git repository inside Jenkins.
Every workflow script in your Jenkins see these shared library scripts in their classpath.


### Directory structure

The directory structure of the shared library repository is as follows:

    (root)
     +- src                     # groovy source files
     |   +- org
     |       +- foo
     |           +- Bar.groovy  # for org.foo.Bar class
     +- vars
         +- foo.groovy          # for global 'foo' variable/function 
         +- foo.txt             # help for 'foo' variable/function

The `src` directory should look like standard Java source directory structure.
This directory is added to the classpath when executing workflows.

The `vars` directory hosts scripts that define global variables accessible from
workflow scripts.
The basename of each `*.groovy` file should be a Groovy (~ Java) identifier, conventionally `camelCased`.
The matching `*.txt`, if present, can contain documentation, processed through the system’s configured markup formatter
(so may really be HTML, Markdown, etc., though the `txt` extension is required).

The groovy source files in these directories get the same sandbox / CPS
transformation just like your workflow scripts.

Other directories under the root are reserved for future enhancements.


### Accessing repository
This directory is managed by Git, and you'll deploy new changes through `git push`.
The repository is exposed in two endpoints:

 * `ssh://USERNAME@server:PORT/workflowLibs.git` through [Jenkins SSH](https://wiki.jenkins-ci.org/display/JENKINS/Jenkins+SSH)
 * `http://server/jenkins/workflowLibs.git` (when your Jenkins is `http://server/jenkins/`). As noted in [JENKINS-26537](https://issues.jenkins-ci.org/browse/JENKINS-26537), this mode will not currently work in an authenticated Jenkins instance.

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

```groovy
// src/org/foo/Point.groovy
package org.foo;

// point in 3D space
class Point {
  float x,y,z;
}
```

However classes written like this cannot call step functions like `sh` or `git`.
More often than not, what you want to define is a series of functions that in turn invoke
other workflow step functions. You can do this by not explicitly defining the enclosing class,
just like your main workflow script itself:

```groovy
// src/org/foo/Zot.groovy
package org.foo;

def checkOutFrom(repo) {
  git url: "git@github.com:jenkinsci/${repo}"
}
```

You can then call such function from your main workflow script like this:

```groovy
def z = new org.foo.Zot()
z.checkOutFrom(repo)
```

### Defining global functions
You can define your own functions that looks and feels like built-in step functions like `sh` or `git`.
For example, to define `helloWorld` step of your own, create a file named `vars/helloWorld.groovy` and
define the `call` method:

```groovy
// vars/helloWorld.groovy
def call(name) {
    // you can call any valid step functions from your code, just like you can from workflow scripts
    echo "Hello world, ${name}"
}
```

Then your workflow can call this function like this:

```groovy
helloWorld "Joe"
helloWorld("Joe")
```

If called with a block, the `call` method will receive a `Closure` object. You can define that explicitly
as the type to clarify your intent, like the following:

```groovy
// vars/windows.groovy
def call(Closure body) {
    node('windows') {
        body()
    }
}
```

Your workflow can call this function like this:

```groovy
windows {
    bat "cmd /?"
}
```

See [the closure chapter of Groovy language reference](http://www.groovy-lang.org/closures.html) for more details
about the block syntax in Groovy.

### Defining global variables
Internally, scripts in the `vars` directory are instantiated as a singleton on-demand, when used first.
So it is possible to define more methods, properties on a single file that interact with each other:

```groovy
// vars/acme.groovy
def setFoo(v) {
    this.foo = v;
}
def getFoo() {
    return this.foo;
}
def say(msg) {
    echo "Hello world, ${name}"
}
```

Then your workflow can call these functions like this:

```groovy
acme.foo = 5;
echo acme.foo; // print 5
acme.say "Joe" // print "Hello world, Joe"
```

### Define more structured DSL
If you have a lot of workflow jobs that are mostly similar, the global function/variable mechanism gives you
a handy tool to build a higher-level DSL that captures the similarity. For example, all Jenkins plugins are
built and tested in the same way, so we might write a global function named `jenkinsPlugin` like this:

```groovy
// vars/jenkinsPlugin.groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    
    // now build, based on the configuration provided
    node {
        git url: "https://github.com/jenkinsci/${config.name}-plugin.git"
        sh "mvn install"
        mail to: "...", subject: "${config.name} plugin build", body: "..."
    }
}
```

With this, the workflow script will look a whole lot simpler, to the point that people who don't know anything
about Groovy can write it:

```groovy
jenkinsPlugin {
    name = 'git'
}
```
