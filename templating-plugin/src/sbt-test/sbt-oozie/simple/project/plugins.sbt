{
  sys.props.get("plugin.version") match {
    case Some(pluginVersion) =>
      addSbtPlugin("com.tapad.sbt" % "sbt-oozie" % pluginVersion)
      addSbtPlugin("com.tapad.sbt" % "sbt-oozie-templating" % pluginVersion)
    case None =>
      sys.error(
        """
        |The system property 'plugin.version' is not defined.
        |Specify this property using the scriptedLaunchOpts -D.
        """.stripMargin.trim
      )
  }
}
