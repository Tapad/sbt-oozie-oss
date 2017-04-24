package sbtoozie

import sbt._
import sbt.Keys._
import sbt.complete._
import sbt.complete.DefaultParsers._
import sbthadoop.HadoopKeys
import scala.util.Try
import org.apache.hadoop.fs.Path
import org.scalactic.{Or, Good, Bad, ErrorMessage}
import com.tapad.oozie.OozieService
import com.tapad.oozie.ProtocolUtils

object OozieSettings {

  import OozieKeys._

  val projectSettings = Seq(
    commands += setOozieUrl,
    oozieUrl := sys.env.getOrElse(
      "OOZIE_URL",
      sys.error(
        "Please declare a value for the `oozieUrl` setting " +
        "or set the OOZIE_URL environmental variable."
      )
    ),
    oozieUser := HadoopKeys.hadoopUser.value,
    oozieService := {
      try {
        val url = new URL(oozieUrl.value)
        OozieService(url, oozieUser.value)
      } catch {
        case e: Exception => sys.error(
          "Unable to instantiate the Oozie service interface: " +
          ErrorHandling.reducedToString(e)
        )
      }
    },
    oozieCoordinators := {
      val filter = OozieParsers.JobsFilter.parsed
      val service = oozieService.value
      val result = service.getCoordinators(filter)
      result match {
        case Good(jobs) => jobs
        case Bad(e) =>
          sys.error(
            "Unexpected error encountered when retrieving Oozie coordinators: " +
            ErrorHandling.reducedToString(e)
          )
      }
    },
    oozieWorkflows := {
      val filter = OozieParsers.JobsFilter.parsed
      val service = oozieService.value
      val result = service.getWorkflows(filter)
      result match {
        case Good(jobs) => jobs
        case Bad(e) =>
          sys.error(
            "Unexpected error encountered when retrieving Oozie workflows: " +
            ErrorHandling.reducedToString(e)
          )
      }
    },
    oozieCoordinatorInfo := {
      val jobId = OozieParsers.CoordinatorJobId.parsed
      val service = oozieService.value
      service.getCoordinatorInfo(jobId) match {
        case Good(job) => job
        case Bad(e) =>
          sys.error(
            s"Unexpected error encountered when retrieving Oozie coordinator ($jobId) info: " +
            ErrorHandling.reducedToString(e)
          )
      }
    },
    oozieWorkflowInfo := {
      val jobId = OozieParsers.WorkflowJobId.parsed
      val service = oozieService.value
      service.getWorkflowInfo(jobId) match {
        case Good(job) => job
        case Bad(e) =>
          sys.error(
            s"Unexpected error encountered when retrieving Oozie workflow ($jobId) info: " +
            ErrorHandling.reducedToString(e)
          )
      }
    },
    oozieUpload := {
      val log = streams.value.log
      for {
        application <- oozieLocalApplicationByNameOrError.evaluated
        applicationName = OozieUtils.getApplicationName(application, oozieLocalApplications.value)
      } yield {
        IO.withTemporaryDirectory { tmp =>
          oozieLocalShare.value.foreach { share =>
            log.info(s"Copying resources from $application and $share to temporary location $tmp")
            OozieUtils.mergeDirectories(share, application, tmp)
          }
          val applicationFilesByName =
            OozieUtils.getFilesByName(application) ++
            OozieUtils.getFilesByName(tmp)
          val applicationPathsByFile = applicationFilesByName
            .map(_.swap)
            .mapValues(new Path(_))
          val hdfs = HadoopKeys.hadoopHdfs.value
          val hdfsTarget = new Path(oozieHdfsApplications.value, applicationName)
          log.info(s"Uploading $applicationName to $hdfsTarget")
          OozieUtils.uploadApplication(hdfs, applicationPathsByFile, hdfsTarget) match {
            case Good(_) => log.debug(s"$applicationName successfully uploaded")
            case Bad(message) => sys.error(message)
          }
        }
      }
    },
    oozieDryRun := {
      val log = streams.value.log
      val service = oozieService.value
      val result = (for {
        application <- oozieLocalApplicationByNameOrError.evaluated
        propertiesFile <- OozieUtils.getApplicationPropertiesFile(application)
        properties = ProtocolUtils.readPropertiesFromFile(propertiesFile)
      } yield {
        service.dryrunJob(properties)
      }).flatMap(identity).fold(
        log.info(_),
        asSysError
      )
    },
    oozieRun := {
      val log = streams.value.log
      val service = oozieService.value
      val result = (for {
        application <- oozieLocalApplicationByNameOrError.evaluated
        propertiesFile <- OozieUtils.getApplicationPropertiesFile(application)
        properties = ProtocolUtils.readPropertiesFromFile(propertiesFile)
      } yield {
        service.runJob(properties)
      }).flatMap(identity).fold(
        log.info(_),
        asSysError
      )
    },
    oozieSubmit := {
      val log = streams.value.log
      val service = oozieService.value
      val result = (for {
        application <- oozieLocalApplicationByNameOrError.evaluated
        propertiesFile <- OozieUtils.getApplicationPropertiesFile(application)
        properties = ProtocolUtils.readPropertiesFromFile(propertiesFile)
      } yield {
        service.submitJob(properties)
      }).flatMap(identity).fold(
        log.info(_),
        asSysError
      )
    },
    oozieStart := {
      val service = oozieService.value
      val result = (for {
        jobIdByLookupKey <- oozieIdByValueOrApplicationName.evaluated
        jobId = jobIdByLookupKey._1
      } yield {
        service.startJob(jobId)
      }).flatMap(identity).badMap(asSysError)
    },
    oozieRerun := {
      lazy val (rerunType, rerunScope) = OozieParsers.RerunTypeAndScope.parsed
      val log = streams.value.log
      val service = oozieService.value
      val result = (for {
        jobIdByLookupKey <- oozieIdByValueOrApplicationName.evaluated
        jobId = jobIdByLookupKey._1
      } yield {
        service.rerunCoordinator(jobId, rerunType, rerunScope).map { actions =>
          val lines = "The following action(s) will be rerun:" +: actions.map(_.getId)
          val message = lines.mkString("\n")
          log.info(message)
        }
      }).flatMap(identity).badMap(asSysError)
    },
    oozieIgnore := {
      lazy val scope = OozieParsers.IgnoreScope.parsed
      val log = streams.value.log
      val service = oozieService.value
      val result = (for {
        jobIdByLookupKey <- oozieIdByValueOrApplicationName.evaluated
        jobId = jobIdByLookupKey._1
      } yield {
        service.ignoreCoordinator(jobId, scope).map { actions =>
          val lines = "The following action(s) will be ignored:" +: actions.map(_.getId)
          val message = lines.mkString("\n")
          log.info(message)
        }
      }).flatMap(identity).badMap(asSysError)
    },
    oozieDryUpdate := {
      val service = oozieService.value
      val applications = oozieLocalApplications.value
      val applicationsByName = OozieUtils.getApplicationsByName(applications)
      val result = (for {
        jobIdByLookupKey <- oozieIdByApplicationName.evaluated
        (jobId, applicationName) = jobIdByLookupKey
        application <- OozieUtils.getApplicationByName(applicationsByName, applicationName)
        propertiesFile <- OozieUtils.getApplicationPropertiesFile(application)
        properties = ProtocolUtils.readPropertiesFromFile(propertiesFile)
      } yield {
        service.updateJob(jobId, properties, dryrun = true)
      }).flatMap(identity).badMap(asSysError)
    },
    oozieUpdate := {
      val service = oozieService.value
      val applications = oozieLocalApplications.value
      val applicationsByName = OozieUtils.getApplicationsByName(applications)
      val result = (for {
        jobIdByLookupKey <- oozieIdByApplicationName.evaluated
        (jobId, applicationName) = jobIdByLookupKey
        application <- OozieUtils.getApplicationByName(applicationsByName, applicationName)
        propertiesFile <- OozieUtils.getApplicationPropertiesFile(application)
        properties = ProtocolUtils.readPropertiesFromFile(propertiesFile)
      } yield {
        service.updateJob(jobId, properties, dryrun = false)
      }).flatMap(identity).badMap(asSysError)
    },
    oozieChange := {
      lazy val changeValue = OozieParsers.ChangeValue.parsed
      val log = streams.value.log
      val service = oozieService.value
      val result = (for {
        jobIdByLookupKey <- oozieIdByValueOrApplicationName.evaluated
        jobId = jobIdByLookupKey._1
      } yield {
        service.changeJob(jobId, changeValue)
      }).flatMap(identity).badMap(asSysError)
    },
    oozieSuspend := {
      val service = oozieService.value
      val result = (for {
        jobIdByLookupKey <- oozieIdByValueOrApplicationName.evaluated
        (jobId, _) = jobIdByLookupKey
      } yield {
        service.suspendJob(jobId)
      }).flatMap(identity).badMap(asSysError)
    },
    oozieKill := {
      val service = oozieService.value
      val result = (for {
        jobIdByLookupKey <- oozieIdByValueOrApplicationName.evaluated
        (jobId, _) = jobIdByLookupKey
      } yield {
        service.killJob(jobId)
      }).flatMap(identity).badMap(asSysError)
    },
    oozieLocalApplications := (resourceDirectory in Compile).value / "oozie" / "applications",
    oozieLocalApplicationNames := {
      OozieUtils.getApplicationsByName(oozieLocalApplications.value).keySet
    },
    oozieLocalApplicationByName := {
      val applicationName = (Space ~> (NotQuoted.examples(oozieLocalApplicationNames.value))).parsed
      val applicationsByName = OozieUtils.getApplicationsByName(oozieLocalApplications.value)
      applicationsByName.get(applicationName)
    },
    oozieLocalApplicationByNameOrError := {
      Or.from(
        oozieLocalApplicationByName.evaluated,
        orElse = "No application exists that matches given input"
      )
    },
    oozieLocalShare := None,
    oozieHdfsApplications := new Path("/oozie/applications"),
    oozieIdByApplicationName := {
      val service = oozieService.value
      val applications = oozieLocalApplications.value
      (for {
        application <- oozieLocalApplicationByNameOrError.evaluated
        propertiesFile <- OozieUtils.getApplicationPropertiesFile(application)
        applicationPath <- OozieUtils.getCoordinatorApplicationPath(propertiesFile)
        applicationName = OozieUtils.getApplicationName(application, applications)
      } yield {
        getJobId(service, applicationPath).map(_ -> applicationName)
      }).flatMap(identity)
    },
    oozieIdByValueOrApplicationName := {
      val service = oozieService.value
      val applications = OozieUtils.getApplicationsByName(oozieLocalApplications.value)
      (
        ( // explicit job id parser
          (OozieParsers.CoordinatorJobId | OozieParsers.WorkflowJobId)
         )
      | ( // application name parser
          (Space ~> (NotQuoted.examples(oozieLocalApplicationNames.value)))
        )
      ).parsed match {
        case jobId if jobId.endsWith("-C") | jobId.endsWith("-W") => Good(jobId -> jobId)
        case applicationName =>
          (for {
            application <- OozieUtils.getApplicationByName(applications, applicationName)
            propertiesFile <- OozieUtils.getApplicationPropertiesFile(application)
            applicationPath <- OozieUtils.getCoordinatorApplicationPath(propertiesFile)
          } yield {
            getJobId(service, applicationPath).map(_ -> applicationName)
          }).flatMap(identity)
      }
    }
  )

  def setOozieUrl = Command.single("oozieSetUrl") { (state, url) =>
    SettingUtils.applySettings(
      state,
      Seq(oozieUrl := url)
    )
  }

  private def getJobId(service: OozieService, applicationPath: String): String Or ErrorMessage = {
    val filter = "status=RUNNING;status=SUSPENDED;status=PREP"
    (for {
      jobs <- service.getCoordinators(filter).badMap(_.getMessage)
    } yield {
      val results = jobs.filter(_.getAppPath == applicationPath).map(_.getId)
      if (results.isEmpty) {
        Bad(s"No Oozie job exists for the application at $applicationPath")
      } else if (results.size > 1) {
        Bad(s"Multiple Oozie job instances exist for the application at $applicationPath")
      } else {
        Good(results.head)
      }
    }).flatMap(identity)
  }

  private def asSysError[A](a: A): Unit = {
    a match {
      case message: String => sys.error(message)
      case e: Exception => sys.error(ErrorHandling.reducedToString(e))
      case other => sys.error("An unexpected error was encountered: " + other)
    }
  }
}
