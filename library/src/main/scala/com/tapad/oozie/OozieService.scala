package com.tapad.oozie

import java.io.File
import java.net.URL
import java.util.Properties
import scala.collection.JavaConverters._
import org.apache.oozie.client._
import org.scalactic.{Or, Good, Bad}

case class OozieService(client: OozieClient, userName: String) {

  import OozieService._

  def getCoordinators(filter: String): Seq[CoordinatorJob] Or OozieClientException = {
    orOozieClientException {
      val offset = 1
      val limit = 10 * 1000
      val f = client.getCoordJobsInfo(_: String, _: Int, _: Int).asScala
      getUntilEmpty(filter, offset, limit)(f)
    }
  }

  def getWorkflows(filter: String): Seq[WorkflowJob] Or OozieClientException = {
    orOozieClientException {
      val offset = 1
      val limit = 10 * 1000
      val f = client.getJobsInfo(_: String, _: Int, _: Int).asScala
      getUntilEmpty(filter, offset, limit)(f)
    }
  }

  def getCoordinatorInfo(jobId: String): CoordinatorJob Or OozieClientException = {
    orOozieClientException {
      client.getCoordJobInfo(jobId)
    }
  }

  def getWorkflowInfo(jobId: String): WorkflowJob Or OozieClientException = {
    orOozieClientException {
      client.getJobInfo(jobId)
    }
  }

  def dryrunJob(props: Properties): String Or OozieClientException = {
    orOozieClientException {
      withConfiguration(props) {
        client.dryrun(_)
      }
    }
  }

  def runJob(props: Properties): String Or OozieClientException = {
    orOozieClientException {
      withConfiguration(props) {
        client.run(_)
      }
    }
  }

  def submitJob(props: Properties): String Or OozieClientException = {
    orOozieClientException {
      withConfiguration(props) {
        client.submit(_)
      }
    }
  }

  def startJob(jobId: String): Unit Or OozieClientException = {
    orOozieClientException {
      client.start(jobId)
    }
  }

  def rerunCoordinator(
    jobId: String,
    rerunType: String,
    rerunScope: String
  ): Seq[CoordinatorAction] Or OozieClientException = {
    val refresh = true
    val noCleanup = false
    orOozieClientException {
      client.reRunCoord(jobId, rerunType, rerunScope, refresh, noCleanup).asScala
    }
  }

  def rerunWorkflow(jobId: String, props: Properties): Unit Or OozieClientException = {
    orOozieClientException {
      withConfiguration(props) {
        client.reRun(jobId, _)
      }
    }
  }

  def ignoreCoordinator(
    jobId: String,
    scope: String
  ): Seq[CoordinatorAction] Or OozieClientException = {
    orOozieClientException {
      client.ignore(jobId, scope).asScala
    }
  }

  def updateJob(jobId: String, props: Properties, dryrun: Boolean): String Or OozieClientException = {
    val showDiff = true
    orOozieClientException {
      withConfiguration(props) {
        client.updateCoord(jobId, _, dryrun.toString, showDiff.toString)
      }
    }
  }

  def changeJob(jobId: String, value: String): Unit Or OozieClientException = {
    orOozieClientException {
      client.change(jobId, value)
    }
  }

  def suspendJob(jobId: String): Unit Or OozieClientException = {
    orOozieClientException {
      client.suspend(jobId)
    }
  }

  def resumeJob(jobId: String): Unit Or OozieClientException = {
    orOozieClientException {
      client.resume(jobId)
    }
  }

  def killJob(jobId: String): Unit Or OozieClientException = {
    orOozieClientException {
      client.kill(jobId)
    }
  }

  private def orOozieClientException[A](f: => A): A Or OozieClientException = {
    try {
      Good(f)
    } catch {
      case e: OozieClientException => Bad(e)
    }
  }

  private def withConfiguration[A](additionalProps: Properties)(f: Properties => A): A = {
    val config = client.createConfiguration()
    config.put(OozieClient.USER_NAME, userName)
    config.putAll(additionalProps)
    f(config)
  }

  private def getUntilEmpty[A](filter: String, offset: Int, limit: Int)(f: (String, Int, Int) => Seq[A]): Seq[A] = {
    var acc = f(filter, offset, limit)
    var next = Seq.empty[A]
    do {
      next = f(filter, offset + limit, limit)
      acc ++ next
    } while (next.nonEmpty)
    acc
  }
}

object OozieService {

  def apply(url: URL, userName: String): OozieService = {
    val client = new OozieClient(url.toString)
    OozieService(client, userName)
  }
}
