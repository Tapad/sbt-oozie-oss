package sbtoozie

import sbt._

object OozieTemplatingKeys {
  val oozieEvaluateTemplates = taskKey[Unit]("Evaluate templates")
}
