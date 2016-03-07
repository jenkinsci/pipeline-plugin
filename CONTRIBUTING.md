# Contributing to the Pipeline plugin

## Legal

The license is MIT. New significant source files (usually `*.java`) should carry the MIT license header.
[Background](https://wiki.jenkins-ci.org/display/JENKINS/Governance+Document#GovernanceDocument-License)

## Suggesting patches

For bug fixes and enhancements to existing Pipeline features, first make sure an issue is filed.
[JIRA query for open `workflow-plugin` issues](https://issues.jenkins-ci.org/secure/IssueNavigator.jspa?reset=true&jqlQuery=project+%3D+JENKINS+AND+resolution+%3D+Unresolved+AND+component+%3D+workflow-plugin+ORDER+BY+key+DESC&mode=hide)

Then you can file a pull request with your change.
(Link to the JIRA issue in the PR, and link to the PR from the JIRA issue.)
Please keep the patch as short as possible to make it easier to review, as well as minimizing the chance of merge conflicts.
In particular, do not include diff hunks which merely change whitespace or reformat lines not otherwise edited.
PRs should include test coverage for the fix or enhancement whenever feasible (most tests are under `aggregator/src/test/java/`).

You can include an addition to the [changelog](CHANGES.md).

## Major work

The procedure would be the same for major new features or refactorings, but you probably want to get advance feedback on the design.
You can use the Jenkins developer list, or contact a maintainer such as `jglick` on `#jenkins` IRC, etc.

## New steps

If you want to contribute new Pipeline steps, consider adding them to an existing plugin which already covers similar functionality.
For general-purpose steps, consider the [Pipeline Utility Steps plugin](https://github.com/jenkinsci/pipeline-utility-steps-plugin).
See also the [plugin compatibility index and guide](COMPATIBILITY.md).

# Development resources

* [Ongoing plugin compatibility list](COMPATIBILITY.md)
* [Source repository](https://github.com/jenkinsci/workflow-plugin)
* [Open pull requests](https://github.com/jenkinsci/workflow-plugin/pulls)
* [CI job](https://jenkins.ci.cloudbees.com/job/plugins/job/workflow-plugin/)
* [Video tutorial on implementing a Step API](http://jenkins-ci.org/content/workflow-plugin-tutorial-writing-step-impl)
* [Video walkthrough of code](https://www.youtube.com/watch?v=tZygoTlW6YE)

## Running from sources

If you want to try running recent development changes, rather than released binaries, you have two options. You can run directly from the source tree; from the root of the repository:

    mvn -DskipTests clean install && mvn -f aggregator hpi:run

Then visit http://localhost:8080/jenkins/ to play with the plugins.

(If your IDE supports compile-on-save mode this is especially convenient since each `hpi:run` will pick up compiled changes from member plugins without needing to run to `package` phase.)

You can also run the Docker demo with snapshot binaries:

    make -C demo run-snapshot

The snapshot Docker demo is mainly useful for verifying the effect of ongoing changes on future demo binary releases. You get the `cd` sample job set up, but your environment is thrown away if you kill the Docker container (for example with Ctrl-C). When using `hpi:run` the same `aggregator/work/` home directory is reused so long as you do not explicitly delete it.

## Source organization

While the implementation is divided into a number of plugins, for ease of prototyping they are all kept in one repository using snapshot dependencies.

* `step-api` defines a generic build step interface (not specific to pipelines) that many plugins could in the future depend on.
* `basic-steps` add some generic step implementations. There is [more documentation there](basic-steps/CORE-STEPS.md).
* `api` defines the essential aspects of pipelines and their executions. In particular, the engine running a Pipeline is extensible and so could in the future support visual orchestration languages.
* `support` adds general implementations of some internals needed by pipelines, such as storing state.
* `job` provides the actual job type and top-level UI for defining and running pipelines.
* `durable-task-step` uses the `durable-task` plugin to define a shell script step that can survive restarts.
* `scm-step` adds SCM-related steps. There is [more documentation there](scm-step/README.md).
* `cps` is the Pipeline engine implementation based on the Groovy language, and supporting long-running pipelines using a _continuation passing style_ transformation of the script.
* `cps-global-lib` adds a Git-backed repository for Groovy libraries available to scripts.
* `aggregator` is a placeholder plugin allowing you to `mvn hpi:run` and see everything working together, as well as holding integration tests.
