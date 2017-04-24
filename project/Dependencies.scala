import sbt._

object Dependencies {

  val ScalaVersion = "2.10.6"

  val SupportedScalaVersions = Seq(ScalaVersion, "2.11.11", "2.12.2")

  val ScalaTestVersion = "3.0.0"

  val ScalacticVersion = ScalaTestVersion

  def parserCombinators(scalaVersion: String): Option[ModuleID] = {
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 10)) => None
      case _ => Some("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4")
    }
  }
}
