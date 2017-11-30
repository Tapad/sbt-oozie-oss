import Dependencies._
import Publishing._

/* The base, minimal settings for every project, including the root aggregate project */
val BaseSettings = Seq(
  organization := "com.tapad.sbt",
  licenses += ("BSD New", url("https://opensource.org/licenses/BSD-3-Clause")),
  scalaVersion := Dependencies.ScalaVersion
)

/* Common settings for all non-aggregate subprojects */
val CommonSettings = BaseSettings ++ Seq(
  scalacOptions ++= Seq("-deprecation", "-language:_"),
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % ScalaTestVersion % "test"
  )
)

val PluginSettings = CommonSettings ++ scriptedSettings ++ Seq(
  sbtPlugin := true,
  scriptedLaunchOpts ++= Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + version.value),
  scriptedBufferLog := false
)

lazy val root = (project in file("."))
  .settings(BaseSettings: _*)
  .settings(NoopPublishingSettings: _*)
  .settings(ReleaseSettings: _*)
  .aggregate(plugin, library, util, templating, templatingLibrary)
  .enablePlugins(CrossPerProjectPlugin)

lazy val plugin = (project in file("plugin"))
  .settings(PluginSettings: _*)
  .settings(PluginPublishingSettings: _*)
  .settings(
    name := "sbt-oozie",
    libraryDependencies ++= Seq(
      "org.slf4j"      % "slf4j-api"    % "1.7.25",
      "org.slf4j"      % "slf4j-jdk14"  % "1.7.25" % "test"
    ),
    publishLocal := {
      (publishLocal.dependsOn(publishLocal in library).dependsOn(publishLocal in util)).value
    },
    addSbtPlugin("com.tapad.sbt" % "sbt-hadoop" % "0.2.0")
  )
  .dependsOn(library, util)

lazy val library = (project in file("library"))
  .settings(CommonSettings: _*)
  .settings(LibraryPublishingSettings: _*)
  .settings(
    name := "oozie-lib",
    libraryDependencies ++= Seq(
      "org.apache.oozie"  % "oozie-client" % "4.3.0" exclude ("org.slf4j", "slf4j-simple"),
      "org.scalactic"    %% "scalactic"    % ScalacticVersion
    ),
    libraryDependencies := scalaXml(scalaVersion.value).fold(libraryDependencies.value) {
      libraryDependencies.value :+ _
    }
  )

lazy val util = (project in file("util"))
  .settings(CommonSettings: _*)
  .settings(LibraryPublishingSettings: _*)
  .settings(
    name := "oozie-util",
    libraryDependencies := parserCombinators(scalaVersion.value).fold(libraryDependencies.value) {
      libraryDependencies.value :+ _
    }
  )

lazy val templating = (project in file("templating-plugin"))
  .settings(PluginSettings: _*)
  .settings(PluginPublishingSettings: _*)
  .settings(
    name := "sbt-oozie-templating",
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "sbtoozie",
    addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.3.3"),
    sourceDirectories in (Compile, TwirlKeys.compileTemplates) += {
      (resourceDirectory in Compile).value / "templates"
    },
    TwirlKeys.templateFormats ++= Map(
      "sh" -> "play.twirl.api.TxtFormat"
    ),
    publishLocal := {
      (publishLocal.dependsOn(publishLocal in plugin)).value
    },
    publishLocal := {
      (publishLocal.dependsOn(publishLocal in templatingLibrary)).value
    }
  )
  .dependsOn(plugin, templatingLibrary)
  .enablePlugins(BuildInfoPlugin, SbtTwirl)

lazy val templatingLibrary = (project in file("templating-library"))
  .settings(CommonSettings: _*)
  .settings(LibraryPublishingSettings: _*)
  .settings(
    name := "oozie-templating-lib",
    libraryDependencies ++= Seq(
      "org.scala-lang"     % "scala-reflect"  % scalaVersion.value,
      "org.slf4j"          % "slf4j-api"      % "1.7.25",
      "com.typesafe.play" %% "twirl-api"      % "1.3.3" % "provided"
    ),
    parallelExecution in test := false
  )
