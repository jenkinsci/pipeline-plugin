# Jenkins Workflow Syntax Reference

# Steps

## `archive`: Archive Artifacts

* `org.jenkinsci.plugins.workflow.steps.ArtifactArchiverStep` - `org.jenkinsci.plugins.workflow.steps.ArtifactArchiverStepExecution`
* params
  * `includes`: String - support environment variables
  * `excludes`: String - support environment variables

## `unarchive`: Copy archived artifacts into the workspace

* `org.jenkinsci.plugins.workflow.steps.ArtifactUnarchiverStep` and `org.jenkinsci.plugins.workflow.steps.ArtifactUnarchiverStepExecution`
* params
  * `mappings`: Map(source, dest) - Files to copy over.

## `step`: General Build Step

* `org.jenkinsci.plugins.workflow.steps.CoreStep` and `org.jenkinsci.plugins.workflow.steps.CoreStep.Execution`
* params: TODO

## `echo`: Print Message

* `org.jenkinsci.plugins.workflow.steps.EchoStep` and `org.jenkinsci.plugins.workflow.steps.EchoStep.Execution`
* params:
  * `message`: String - TODO why doesn't it support environment variables?

## `dir`: Change Directory

* `org.jenkinsci.plugins.workflow.steps.PushdStep` and `org.jenkinsci.plugins.workflow.steps.PushdStep.Execution`
* Params:
  * `path`: String - absolute or relative. TODO does it support environment variables?

## `pwd`: Determine Current Directory

* `org.jenkinsci.plugins.workflow.steps.PwdStep` and `org.jenkinsci.plugins.workflow.steps.PwdStep.Execution`
* Params: none

## `readFile`: Read file from workspace

* `org.jenkinsci.plugins.workflow.steps.ReadFileStep` and `org.jenkinsci.plugins.workflow.steps.ReadFileStep.Execution`
* Params:
  * `file`: String - MUST be a relative path (NO absolute path)
  * `encoding`: String

## `retry`: Retry the body up to N times

* `org.jenkinsci.plugins.workflow.steps.RetryStep` and `org.jenkinsci.plugins.workflow.steps.RetryStepExecution`
* Params:
  * `count` : int - Number of retries
  * Nested block

## `tool`: Install a tool

* `org.jenkinsci.plugins.workflow.steps.ToolStep` and `org.jenkinsci.plugins.workflow.steps.ToolStep.Execution`
* Params:
  * `name`: String - Name of the Tool to install
  * `type`: TODO

## `writeFile`: Write file to workspace

* `org.jenkinsci.plugins.workflow.steps.WriteFileStep` and `org.jenkinsci.plugins.workflow.steps.WriteFileStep.Execution`
* Params:
  * `file`: String - MUST be a relative path (NO absolute path)
  * `encoding`: String
  * `text`: String -

## `input`: Input

* `org.jenkinsci.plugins.workflow.steps.input.InputStep` and ``
* Params:
  * `message` : String, default "Workflow has paused and needs your input before proceeding"
  * `id`: Optional ID that uniquely identifies this input from all others.
  * `submitter`: Optional user/group name who can approve this.
  * `ok`: Optional caption of the OK button
  * `parameters`: List<ParameterDefinition>

## `build`: Build a job

* `org.jenkinsci.plugins.workflow.steps.build.BuildTriggerStep` and `org.jenkinsci.plugins.workflow.steps.build.BuildTriggerStepExecution`
* Params:
  * `job`: String name of the Job to trigger
  * `parameters`: List<ParameterValue>

## `parallel`: Execute sub-workflows in parallel

* `org.jenkinsci.plugins.workflow.cps.steps.ParallelStep` and ``
* Params:
  * ``:

## `semaphore`: Test step - TODO check display name

Step that blocks until signaled.

* `org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep` and `org.jenkinsci.plugins.workflow.steps.StepExecution`
* Params:
  * ``:

## `git`: Git

* `org.jenkinsci.plugins.workflow.steps.scm.SCMStep` and ``
* Params:
  * `url`: String
  * `branch`: String - default "master"
  * `poll`: boolean - default true
  * `changelog`: boolean - default true

## `svn`: Subversion

* `org.jenkinsci.plugins.workflow.steps.scm.SubversionStep` and ``
* Params:
  * `url`: String
  * `poll`: boolean - default true
  * `changelog`: boolean - default true

## `ws`: Allocate workspace

* `org.jenkinsci.plugins.workflow.support.steps.WorkspaceStep` and ``
* Params:
  * Nested block

## `load`: Evaluate a Groovy source file into the workflow script

* `org.jenkinsci.plugins.workflow.cps.steps.LoadStep` and ``
* Params:
  * `path`: Relative path of the script within the current workspace.

## `node`: Allocate node

* `org.jenkinsci.plugins.workflow.support.steps.ExecutorStep` and ``
* Params:
  * `label`: String - label or name of a slave
  * Nested block

## `tmpDir`: Set temporary directory as the contextual FilePath

* `org.jenkinsci.plugins.workflow.test.steps.TmpDirStep` and ``
* Params:none

## `catchError`: Catch Error and Continue

* `org.jenkinsci.plugins.workflow.steps.CatchErrorStep` and `org.jenkinsci.plugins.workflow.steps.CatchErrorStep.Execution`
* Params:
  * Nested block

## `stage`: Stage

* `org.jenkinsci.plugins.workflow.support.steps.StageStep` and ``
* Params:
  * `name`: String, mandatory
  * `concurrency`:

## `bat`: Windows Batch Script

* `org.jenkinsci.plugins.workflow.steps.durable_task.BatchScriptStep` and ``
* Params:
  * `script`: String - the script to execute


## `sh`: Shell Script

* `org.jenkinsci.plugins.workflow.steps.durable_task.ShellStep` and ``
* Params:
  * `script`: String - the script to execute


## `scm`: General SCM

* `org.jenkinsci.plugins.workflow.steps.scm.GenericSCMStep` and ``
* Params:
  * `scm`: TODO
