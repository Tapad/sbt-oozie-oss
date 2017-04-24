package sbtoozie

import sbt._
import sbthadoop.HadoopPlugin

object OoziePlugin extends AutoPlugin {

  override def requires = HadoopPlugin

  object autoImport {
    val OozieKeys     = sbtoozie.OozieKeys
    val OozieSettings = sbtoozie.OozieSettings
    val oozieUrl      = OozieKeys.oozieUrl
  }

  override def projectSettings = OozieSettings.projectSettings
}
