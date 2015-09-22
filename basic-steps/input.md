# Introduction

The `input` step pauses flow execution and allows the user to interact and control the flow of the build.

Only a basic "process" or "abort" option is provided in the stage view.

You can optionally request information back, hence the name of the step.

# Accessing the parameter entry screen

## Build console log
You can access the parameter entry screen via a link at the bottom of the build console log.

```text
Running: Input
Input requested
```

Clicking on the "Input requested" link will take you the parameter entry screen.


## "Paused for input?"

You can also access parameter entry screen via link in the sidebar for a build

Click on the link to go directly to the parameter entry screen


# Basic Usage

The default `message` parameter gives a prompt which will be shown to a human.

## Syntax
```groovy
node('remote') {
  input 'Ready to go?'
}
```

## Output

When you run a new build, you will see the following in the stage view.

```text
Running: Input
Ready to go?
Proceed or Abort
```

If you click _Proceed_ the build will proceed to the next step.

If you click _Abort_ the build will be aborted.


# Parameter Usage

## Syntax

```groovy
def runTests = 
  input id: 'run-test-suites',
        message: 'Workflow Configuration',
        ok: 'Proceed',
        parameters: [[ 
          $class: 'BooleanParameterDefinition',
          defaultValue: true,
          name: 'Run test suites?'
        ]]
```

## Parameter Entry Screen

On the parameter entry screen you are able to enter values for parameters that are defined by the input step.


```text
Workflow Configuration

 Run test suites? [X]
 
[Proceed] [Abort]
```

If you click _Proceed_ the build will set the `runTests` variable to the value from the checkbox - and then proceed to the next step.  `runTests` will contain a `java.lang.Boolean`

If you click _Abort_ the build will be aborted.

