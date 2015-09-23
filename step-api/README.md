# Writing Workflow steps

Plugins can implement custom Workflow steps with specialized behavior by adding a dependency on `workflow-step-api`.

## Creating a basic synchronous step

When a Workflow step does something quick and nonblocking, you can make a “synchronous” step.
The Groovy execution waits for it to finish.

Extend `AbstractStepImpl`.
Define mandatory parameters in a `@DataBoundConstructor`.
Define optional parameters using `@DataBoundSetter`.
(Both need matching getters.)

Create a class, conventionally a nested `public static class Execution`, and extend `AbstractSynchronousNonBlockingStepExecution` (or `AbstractSynchronousStepExecution` in older versions of Workflow or for certain trivial steps).
Parameterize it with the desired return value of the step (or `Void` if it need not return a value).
The `run` method should do the work of the step.
You can `@Inject` the step object to access its configuration.
Use `@StepContextParameter` to inject contextual objects you require, as enumerated in `StepContext.get` Javadoc;
commonly required types include `Run`, `TaskListener`, `FilePath`, `EnvVars`, and `Launcher`.

Extend `AbstractStepDescriptorImpl`.
Pass the execution class to the super constructor.
Besides a display name, pick a function name which will be used from Groovy scripts.

Create a `config.jelly` form with databinding for all the parameters, for use from _Snippet Generator_.
You can use the `StepConfigTester` test utility in `workflow-step-api` (`tests` classifier) to verify that all fields are correctly bound.
The descriptor can also have the usual methods complementing `config.jelly` for field validation, etc.

## Creating an asynchronous step

For the more general case that a Workflow step might block in network or disk I/O, and might need to survive Jenkins restarts, you can use a more powerful API.
This relies on a callback system: the Workflow engine tells your step when to start, and your step tells Workflow when it is done.

Extend `AbstractStepExecutionImpl` rather than `AbstractSynchronousStepExecution`.
You will be implementing a `start` method.
Normally it should do any quick setup work and then return `false`, meaning the step is still running.
Later you can call `getContext().onSuccess(returnValue)` (once) to make the step complete normally.
Or, `getContext().onFailure(error)` to make the step throw an exception.

Make sure all your injected parameters are `transient`; in the case of the step object (if configuration is needed), use `@com.google.inject.Inject(optional=true)`.
You can keep other `transient` fields too; override `onResume` to recreate transient state after a Jenkins restart if you need to.
You can also keep non-`transient` fields, assuming they are `Serializable`.
Do not forget to declare

```java
private static final long serialVersionUID = 1L;
```

You should also implement `stop` to terminate the step.
It could simply

```java
getContext().onFailure(cause);
```

but generally it will need to interrupt whatever process you started.

## Creating a block-scoped step

Workflow steps can also take “closures”: a code block which they may run zero or more times, optionally with some added context.

Override `takesImplicitBlockArgument` in your descriptor.
In `start`, or thereafter, call

```java
getContext().newBodyInvoker().
        withContext(…something…).
        withCallback(BodyExecutionCallback.wrap(getContext())).
        start();
```

The above returns the same value as the block.
The callback may also be a `TailCall` to do some cleanup,
or any other `BodyExecutionCallback` to customize handling of the end of the block.

You can pass various contextual objects, as per `@StepContextParameter` above.

`stop` is optional.

## Using more APIs

You can also add a dependency on `workflow-api` which brings in more Workflow-specific features.
For example you can then receive a `FlowNode` as a `@StepContextParameter` and call `addAction` to customize the _Workflow Steps_ view.
