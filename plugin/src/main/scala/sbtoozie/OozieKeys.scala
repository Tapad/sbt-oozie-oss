package sbtoozie

import sbt._
import org.apache.oozie.client.{Job, CoordinatorJob, WorkflowJob}
import org.apache.hadoop.fs.Path
import org.scalactic.{Or, ErrorMessage}
import com.tapad.oozie.OozieService

object OozieKeys {
  val oozieUrl = taskKey[String]("The Oozie server URL")
  val oozieUser = taskKey[String]("Username that will be used when interacting with Oozie")
  val oozieService = taskKey[OozieService]("A handle to the Oozie service for the server located at `oozieUrl`")
  val oozieUpload = inputKey[Unit Or ErrorMessage]("Upload a given Oozie application to HDFS")
  val oozieCoordinators = inputKey[Seq[CoordinatorJob]]("Retrieve the listing of coordinator jobs that match some given criteria")
  val oozieWorkflows = inputKey[Seq[WorkflowJob]]("Retrieve the listing of workflow jobs that match some given criteria")
  val oozieCoordinatorInfo = inputKey[CoordinatorJob]("Retrieve information about a given coordinator (via ID or name)")
  val oozieWorkflowInfo = inputKey[WorkflowJob]("Retrieve information about a given workflow (via ID or name)")
  val oozieDryRun = inputKey[Unit]("Test a given Oozie application by executing a dryrun")
  val oozieRun = inputKey[Unit]("Run a given Oozie application")
  val oozieSubmit = inputKey[Unit]("Submit a given Oozie application")
  val oozieStart = inputKey[Unit]("Start a submitted Oozie (via ID or name)")
  val oozieRerun = inputKey[Unit]("Rerun one or more Oozie coordinator actions (via ID or name)")
  val oozieIgnore = inputKey[Unit]("Ignore one or more Oozie coordinator actions (via ID or name)")
  val oozieDryUpdate = inputKey[Unit]("Test updating an Oozie job by executing a dryrun")
  val oozieUpdate = inputKey[Unit]("Update a running Oozie application applying any changes that exist locally")
  val oozieChange = inputKey[Unit]("Change a value (endtime, concurrency, or pausetime) of an Oozie coordinator")
  val oozieSuspend = inputKey[Unit]("Suspend an Oozie job (via ID or name)")
  val oozieResume = inputKey[Unit]("Suspend an Oozie job (via ID or name)")
  val oozieKill = inputKey[Unit]("Kill an Oozie job (via ID or name)")

  val oozieLocalApplications = settingKey[File]("The local directory in this project that contains Oozie applications")
  val oozieLocalApplicationNames = settingKey[Set[String]]("The resolved, relativized names of the local Oozie applications")
  val oozieLocalApplicationByName = inputKey[Option[File]]("Retrieve the absolute path of a local Oozie application by its name")
  val oozieLocalApplicationByNameOrError = inputKey[File Or ErrorMessage]("Retrieve the absolute path of a local Oozie application by its name")
  val oozieLocalShare = settingKey[Option[File]]("A local directory that contains resources that will be included in every Oozie application")

  val oozieHdfsApplications = settingKey[Path]("The remote path where Oozie applications will exist")

  val oozieIdByApplicationName = inputKey[(String, String) Or ErrorMessage]("Attempt to find a valid, existing job ID for a given local Oozie application")
  val oozieIdByValueOrApplicationName = inputKey[(String, String) Or ErrorMessage]("Accept an explicit job ID or perform a lookup by a local application name")
}
