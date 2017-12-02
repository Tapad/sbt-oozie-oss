# sbt-oozie-oss
An [sbt](http://scala-sbt.org) plugin for launching and scheduling [Oozie](http://oozie.apache.org/) applications.

## Table of contents
* [Requirements](#requirements)
* [Installation](#installation)
* [Usage](#usage)
  * [Integration with sbt-hadoop](#integration-with-sbt-hadoop)
  * [Uploading application resources to HDFS](#uploading-application-resources-to-HDFS)
  * [Running an adhoc workflow](#running-an-adhoc-workflow)
  * [Scheduling a workflow for periodic execution](#scheduling-a-workflow-for-periodic-execution)
  * [Templating](#templating)
  * [Available commands](#available-commands)
* [Contributing](#contributing)
  * [Project structure](#project-structure)
  * [Running tests](#running-tests)
  * [Releasing artifacts](#releasing-artifacts)

## Requirements
- sbt (0.13.5+ or 1.0.0+)
- An installation of Hadoop (>= 2.6.x) to target
- An installation of Oozie (>= 4.0.x) to target

## Installation
Add the following line to `project/plugins.sbt`. See the [Using plugins](http://www.scala-sbt.org/release/docs/Using-Plugins.html) section of the sbt documentation for more information.

```
addSbtPlugin("com.tapad.sbt" % "sbt-oozie" % "0.1.0")
```

## Usage
By default, sbt-oozie assumes the `OOZIE_URL` environment variable is set and is pointing to the Oozie server's request endpoint (e.g. `http://my-oozie-server:11000/oozie`.

If the `OOZIE_URL` environment variable cannot be set, the Oozie server URL can be provided to the `oozieUrl` setting key in your build definition.

Remember to enable the `OoziePlugin` in your build definition, or if using a multi-project setup, on each subproject that you wish to integrate with Oozie.

```
hadoopClasspath := hadoopClasspathFromExecutable.value // used by sbt-hadoop, see next section of README.md

oozieUrl := "http://my-oozie-server:11000/oozie" // required iff OOZIE_URL env variable is not set

enablePlugins(OoziePlugin)
```

### Integration with sbt-hadoop
sbt-oozie will transitively bring [sbt-hadoop](https://github.com/Tapad/sbt-hadoop-oss) into your project once enabled.

sbt-hadoop is used for uploading necessary artifacts (Oozie workflow and coordinator definitions, and if necessary, application JAR files) to HDFS, or any file system or storage backend that implements the Hadoop `FileSystem` abstraction (e.g. Amazon S3, Google Cloud Storage).

### Uploading application resources to HDFS
The `oozieUpload` task is used to upload a given Oozie application to HDFS for subsequent submittal, execution, or scheduling.

From the sbt project which has enabled the `OoziePlugin`, issue:
```
> oozieUpload <name of application>
```

Tab-completion is supported:
```
> oozieUpload <TAB>
baz/quux   baz/qux    foo
```

```
> oozieUpload foo
[info] Uploading foo to /oozie/applications/foo
```

Your application will be uploaded to the path defined by the application's `oozie.wf.application.path` or `oozie.coord.application.path` property.

### Running an adhoc workflow
To run an Oozie workflow that has been [uploaded](#uploading-application-resources-to-HDFS) to HDFS, invoke the `oozieRun` task.

`oozieRun` takes, as a parameter, the name of an application that exists in your project.

An Oozie application is any directory that exists in your project's `oozieLocalApplications` directory that contains a `workflow.xml` file.

Tab-completion is supported:
```
> oozieRun <TAB>
baz/quux   baz/qux    foo
```

```
> oozieRun foo
[info] 0044773-170912171845818-oozie-oozi-W
```

The workflow ID of your application will be printed to the log.

### Scheduling a workflow for periodic execution
Scheduling an Oozie coordinator that has been [uploaded](#uploading-application-resources-to-HDFS) to HDFS also uses the `oozieRun` task.

An Oozie coordinator application has the same requirements as a workflow application with the addition of also including a `coordinator.xml` file.

Again, `oozieRun` takes the name of your Oozie application as a parameter.

Tab-completion is supported:
```
> oozieRun <TAB>
baz/quux   baz/qux    foo
```

```
> oozieRun baz/quux
[info] 0044790-170912171845818-oozie-oozi-C
```

The coordinator ID of your application will be printed to the log.

Kill a running coordinator by application name or by coordinator ID using `oozieKill`:
```
> oozieKill baz/quux
```
```
> oozieKill 0044790-170912171845818-oozie-oozi-C
```

### Templating
The [twirl templating engine](https://github.com/playframework/twirl) can be leveraged to help author Oozie applications.

NOTE: If using sbt 1.0.x, sbt-twirl requires that you use sbt version 1.0.1 or greater due to the `Append` instance backwards compatibility issue addressed in the [release notes](http://www.scala-sbt.org/1.x/docs/sbt-1.0-Release-Notes.html#WatchSource).

Add the following lines to `project/plugins.sbt`.
```
addSbtPlugin("com.tapad.sbt" % "sbt-oozie" % "0.1.0")

addSbtPlugin("com.tapad.sbt" % "sbt-oozie-templating" % "0.1.0")
```

Also be sure to enable both the sbt-oozie and the sbt-oozie-templating plugins in your `build.sbt` file:
```
enablePlugins(OoziePlugin, OozieTemplatingPlugin)
```

Add your twirl templates to the location specifed by `oozieTemplating:sourceDirectory`. By default, this location will be the `templates` subdirectory inside of your project's resources directory (e.g. `src/main/resources/templates`).

For the following example, a template with the file name `workflow.scala.xml` has been added to `src/main/resources/templates/my_oozie_application/workflow.scala.xml`. Its contents are:
```
@()
<workflow>@helper("bar")</workflow>
```

The helper template is placed alongside `workflow.scala.xml`, in the same directory, in a file called `helper.scala.xml`:
```
@(foo: String)
<foo>@{foo}</foo>
```

Generate your Oozie application by invoking `oozieEvaluateTemplates`. The results from evaluating these templates will be placed in `oozieTemplating:target`, which by default, will be the `generated` subdirectory of your project's resources directory (e.g. `src/main/resources/generated`).

The values for the settings provided by sbt-oozie-templating, given a default build definition, can be found in the table below:

| Setting key (scope:name)   | Default value                |
| -------------------------- | ---------------------------- |
| templating:sourceDirectory | src/main/resources/templates |
| templating:target          | src/main/resources/generated |

These can be customized to suit your project's structure.

Non-templated, static resources will be copied to `oozieTemplating:target` with no modifications made to their contents.

sbt-oozie-templating need not only be used for templating Oozie XML files. It is possible to template any type of resource and evaluate it for inclusion in your application. This is a handy way of providing boilerplate and snippets that typically would have been copied from application to application.

For more information, refer to the scripted integration test found at [templating-plugin/src/sbt-test/sbt-oozie/simple](templating-plugin/src/sbt-test/sbt-oozie/simple).

### Available commands
Various Oozie functionality is exposed by the sbt-oozie plugin. The following settings, tasks, and commands are available:

| Setting, task, or command name | Description |
| ------------------------------ | ----------- |
| oozieUrl | The Oozie server URL |
| oozieUser | Username that will be used when interacting with Oozie |
| oozieUpload | Upload a given Oozie application to HDFS |
| oozieCoordinatorInfo | Retrieve information about a given coordinator (via ID or name) |
| oozieWorkflowInfo | Retrieve information about a given workflow (via ID or name) |
| oozieDryRun | Test a given Oozie application by executing a dryrun |
| oozieRun | Run a given Oozie application |
| oozieSubmit | Submit a given Oozie application |
| oozieStart | Start a submitted Oozie (via ID or name) |
| oozieRerun | Rerun one or more Oozie coordinator actions (via ID or name) |
| oozieIgnore | Ignore one or more Oozie coordinator actions (via ID or name) |
| oozieDryUpdate | Test updating an Oozie job by executing a dryrun |
| oozieUpdate | Update a running Oozie application applying any changes that exist locally |
| oozieChange | Change a value (endtime, concurrency, or pausetime) of an Oozie coordinator |
| oozieSuspend | Suspend an Oozie job (via ID or name) |
| oozieResume | Suspend an Oozie job (via ID or name) |
| oozieKill | Kill an Oozie job (via ID or name) |
| oozieLocalApplications | The local directory in this project that contains Oozie applications |
| oozieLocalShare | A local directory that contains resources that will be included in every Oozie application |
| oozieHdfsApplications | The remote path where Oozie applications will exist |

## Contributing

### Project structure
- plugin
- library
- templating-plugin
- templating-library
- util

#### plugin
The necessary wiring to that exposes Oozie functionality via sbt.

#### library
An underlying service used to interface with the Oozie Java client.

#### templating-plugin
An sbt plugin that provides the capability to template Oozie applications using [twirl](https://github.com/playframework/twirl).

#### templating-library
Supporting library code leveraged by the templating plugin.

#### util
Supporting library code and common abstractions.

### Running tests
Although unit tests exist, the main features and functionality of `sbt-oozie` and `sbt-oozie-templating` are tested using sbt's [`scripted-plugin`](https://github.com/sbt/sbt/tree/0.13/scripted). `scripted` tests exist in the `src/sbt-test` directories of the `sbt-oozie` and `sbt-oozie-templating` subprojects.

To run these tests, issue `scripted` from an sbt session after targeting either of these subprojects:

```
$ sbt
> project plugin
> scripted
> project templatingPlugin
> scripted
```

To selectively run a single `scripted` test suite, issue `scripted <name of plugin>/<name of test project>`. e.g. `scripted sbt-oozie/simple`.

Please note that `publishLocal` will be invoked when running `scripted`. `scripted` tests take longer to run than unit tests and will log myriad output to stdout. Also note that any output written to stderr during the execution of a `scripted` test will result in `ERROR` level log entries. These log entries will not effect the resulting status of the actual test.

### Releasing artifacts
`sbt-oozie` uses [https://github.com/sbt/sbt-release](sbt-release). Simply invoke `release` from the root project to release all artifacts.
