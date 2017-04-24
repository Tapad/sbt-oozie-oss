import sbt._
import sbt.Keys._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._
import bintray.BintrayKeys._

object Publishing {

  val PublishingSettings = Seq(
    autoAPIMappings := true,
    bintrayOrganization := Some("tapad-oss"),
    pomIncludeRepository := { _ => false },
    publishArtifact in Test := false,
    publishArtifact in (Compile, packageDoc) := true,
    publishArtifact in (Compile, packageSrc) := true
  )

  val CrossPublishingSettings = PublishingSettings ++ Seq(
    crossScalaVersions := Dependencies.SupportedScalaVersions
  )

  /* `publish` performs a no-op */
  val NoopPublishingSettings = Seq(
    packagedArtifacts in RootProject(file(".")) := Map.empty,
    publish := (),
    publishLocal := (),
    publishArtifact := false,
    publishTo := None
  )

  val PluginPublishingSettings = PublishingSettings ++ Seq(
    bintrayRepository := "sbt-plugins"
  )

  val LibraryPublishingSettings = CrossPublishingSettings ++ Seq(
    bintrayRepository := "maven",
    bintrayPackage := "sbt-oozie-libs",
    publishMavenStyle := true,
    pomIncludeRepository := { _ => false },
    homepage := Some(new URL("https://github.com/Tapad/sbt-oozie-oss")),
    pomExtra := {
      <developers>
        <developer>
          <name>Jeffrey Olchovy</name>
          <id>jeffreyolchovy</id>
          <email>jeffo@tapad.com</email>
          <url>https://github.com/jeffreyolchovy</url>
        </developer>
      </developers>
      <scm>
        <url>https://github.com/Tapad/sbt-oozie-oss</url>
        <connection>scm:git:git://github.com/Tapad/sbt-oozie-oss.git</connection>
      </scm>
    }
  )

  val ReleaseSettings = Seq(
    releaseCrossBuild := true,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      releaseStepCommandAndRemaining("+test"),
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("+publish"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )
}
