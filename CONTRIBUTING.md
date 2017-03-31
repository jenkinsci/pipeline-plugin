# Contributing to the Pipeline plugin

## Legal

The license is MIT. New significant source files (usually `*.java`) should carry the MIT license header.
[Background](https://wiki.jenkins-ci.org/display/JENKINS/Governance+Document#GovernanceDocument-License)

## Suggesting patches

For bug fixes and enhancements to existing Pipeline features, first make sure an issue is filed.
[JIRA query for open Pipeline-related issues](https://issues.jenkins-ci.org/issues/?jql=project%20%3D%20JENKINS%20AND%20resolution%20%3D%20Unresolved%20AND%20%28component%20in%20%28pipeline%2C%20pipeline-build-step-plugin%2C%20pipeline-graph-analysis-plugin%2C%20pipeline-input-step-plugin%2C%20pipeline-milestone-step-plugin%2C%20pipeline-stage-step-plugin%2C%20pipeline-stage-view-plugin%2C%20workflow-aggregator-plugin%2C%20workflow-api-plugin%2C%20workflow-basic-steps-plugin%2C%20workflow-cps-global-lib-plugin%2C%20workflow-cps-plugin%2C%20workflow-durable-task-step-plugin%2C%20workflow-job-plugin%2C%20workflow-multibranch-plugin%2C%20workflow-scm-step-plugin%2C%20workflow-step-api-plugin%2C%20workflow-support-plugin%29%20OR%20labels%20in%20%28pipeline%29%29%20ORDER%20BY%20component%20ASC%2C%20key%20DESC&mode=hide)

Determine the plugin, and thus repository, where the change should be made; for example, `workflow-basic-steps-plugin`.

Then you can file a pull request with your change.
(Link to the JIRA issue in the PR, and link to the PR from the JIRA issue.)
Please keep the patch as short as possible to make it easier to review, as well as minimizing the chance of merge conflicts.
In particular, do not include diff hunks which merely change whitespace or reformat lines not otherwise edited.
PRs should include test coverage for the fix or enhancement whenever feasible.

## Major work

The procedure would be the same for major new features or refactorings, but you probably want to get advance feedback on the design.
You can use the Jenkins developer list, or contact a maintainer such as `jglick` on `#jenkins` IRC, etc.

## New steps

If you want to contribute new Pipeline steps, consider adding them to an existing plugin which already covers similar functionality.
For general-purpose steps, consider the [Pipeline Utility Steps plugin](https://github.com/jenkinsci/pipeline-utility-steps-plugin).
See also the [plugin compatibility guide](DEVGUIDE.md).

# Development resources

* [Ongoing plugin compatibility list](COMPATIBILITY.md)
* [Video tutorial on implementing a Step API](https://jenkins.io/blog/2014/07/08/workflow-plugin-tutorial-writing-a-step-impl/)
* [Video walkthrough of code](https://www.youtube.com/watch?v=tZygoTlW6YE)

## Running from sources

If you want to try running recent development changes, rather than released binaries, you have two options.
You can run directly from a source tree; from the root of a plugin repository:

    mvn hpi:run

Then visit http://localhost:8080/jenkins/ to play with the plugins.

(If your IDE supports compile-on-save mode this is especially convenient since each `hpi:run` will pick up compiled changes without needing to run to `package` phase.)

You can also run the basic Docker demo with snapshot binaries:

* (re-)build one or more component plugins using `mvn -DskipTests clean install`
* specify a `2.x-SNAPSHOT` version in `workflow-aggregator-plugin/demo/plugins.txt`
* run: `make -C workflow-aggregator-plugin/demo run`

(A similar procedure works for other Docker-based demos, such as in `github-branch-source-plugin`.)

The snapshot Docker demo is mainly useful for verifying the effect of ongoing changes on future demo binary releases.
You get the `cd` sample job set up, but your environment is thrown away if you kill the Docker container (for example with Ctrl-C).
When using `hpi:run` the same `work/` home directory is reused so long as you do not explicitly delete it.

## Source organization

The implementation is divided into a number of plugin repositories which can be built and released independently in most cases.

* `workflow-step-api-plugin` defines a generic build step interface (not specific to pipelines) that many plugins could in the future depend on.
* `workflow-basic-steps-plugin` add some generic step implementations.
* `workflow-api-plugin` defines the essential aspects of pipelines and their executions. In particular, the engine running a Pipeline is extensible and so could in the future support visual orchestration languages.
* `workflow-support-plugin` adds general implementations of some internals needed by pipelines, such as storing state.
* `workflow-job-plugin` provides the actual job type and top-level UI for defining and running pipelines.
* `workflow-durable-task-step-plugin` allows you to allocate nodes and workspaces, and uses the `durable-task` plugin to define a shell script step that can survive restarts.
* `workflow-scm-step-plugin` adds SCM-related steps.
* `pipeline-build-step-plugin`, `pipeline-input-step-plugin`, and `pipeline-stage-step-plugin` add complicated steps.
* `pipeline-stage-view-plugin` adds a job-level visualization of builds and stages in a grid.
* `workflow-cps-plugin` is the Pipeline engine implementation based on the Groovy language, and supporting long-running pipelines using a _continuation passing style_ transformation of the script.
* `workflow-cps-global-lib-plugin` adds a Git-backed repository for Groovy libraries available to scripts.
* `workflow-aggregator-plugin` is a placeholder plugin allowing the whole Pipeline suite to be installed with one click. It also hosts the official Docker demo.
