package sbtoozie

import java.io.File
import sbt._
import Keys._
import play.twirl.sbt.SbtTwirl
import play.twirl.sbt.Import.TwirlKeys

object OozieTemplatingPlugin extends AutoPlugin {

  object autoImport {
    val OozieTemplating = config("oozieTemplating").extend(Compile)
    val OozieTemplatingKeys = sbtoozie.OozieTemplatingKeys
    val oozieEvaluateTemplates = OozieTemplatingKeys.oozieEvaluateTemplates
  }

  import autoImport._

  override def requires = OoziePlugin && SbtTwirl

  override def projectSettings = inConfig(OozieTemplating)(scopedSettings) ++ Seq(
    ivyConfigurations += OozieTemplating,
    TwirlKeys.templateFormats ++= Map("properties" -> "play.twirl.api.TxtFormat"),
    sourceDirectories in (Compile, TwirlKeys.compileTemplates) += (sourceDirectory in OozieTemplating).value,
    oozieEvaluateTemplates := {
      val log = streams.value.log
      getTemplatedApplicationsByName((sourceDirectory in OozieTemplating).value).map {
        case (name, dir) =>
          log.info(s"Generating templated application '$name'")
          val classRunner = (runner in Compile).value
          val mainClass = "sbtoozie.TemplateEvaluatorFacade"
          val classpathFiles =
            (fullClasspath in (Compile, TwirlKeys.compileTemplates)).value.files ++
            (fullClasspath in Compile).value.files ++
            (managedClasspath in OozieTemplating).value.files
          val opts = Seq(
            dir.getAbsolutePath,
            (sourceDirectory in OozieTemplating).value.getAbsolutePath,
            (target in OozieTemplating).value.getAbsolutePath
          )
          classRunner.run(mainClass, classpathFiles, opts, log)
      }
    },
    oozieEvaluateTemplates := {
      oozieEvaluateTemplates.dependsOn(compile in (Compile, TwirlKeys.compileTemplates)).value
    },
    clean := {
      clean.dependsOn(clean in OozieTemplating).value
    },
    libraryDependencies ++= Seq(
      "com.tapad.sbt" %% "oozie-templating-lib" % BuildInfo.version % OozieTemplating.name,
      "org.slf4j" % "slf4j-simple" % "1.7.25" % OozieTemplating.name
    ),
    managedClasspath in OozieTemplating := {
      val artifactTypes: Set[String] = (classpathTypes in OozieTemplating).value
      Classpaths.managedJars(OozieTemplating, artifactTypes, update.value)
    }
  )

  private def scopedSettings = Seq(
    sourceDirectory := OozieKeys.oozieLocalApplications.value.getParentFile / "templates",
    target := OozieKeys.oozieLocalApplications.value / "generated",
    clean := {
      streams.value.log.info("Deleting contents of " + target.value)
      IO.delete(target.value)
      IO.createDirectory(target.value)
    }
  )

  private def getTemplatedApplicationsByName(dir: File): Map[String, File] = {
    val withWorkflowXml = (PathFinder(dir) ** "workflow.xml").get
    val withTemplatedWorkflowXml = (PathFinder(dir) ** "workflow.scala.xml").get
    (withWorkflowXml ++ withTemplatedWorkflowXml).map { file =>
      val applicationDir = file.getParentFile
      val applicationName = OozieUtils.getApplicationName(applicationDir, dir)
      applicationName -> applicationDir
    }(scala.collection.breakOut)
  }
}
