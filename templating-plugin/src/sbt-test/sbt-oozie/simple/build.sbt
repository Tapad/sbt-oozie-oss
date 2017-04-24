name := "oozie-templating-simple"

organization := "com.tapad.sbt"

version := "0.1.0"

hadoopClasspath := hadoopClasspathFromExecutable.value

enablePlugins(OoziePlugin, OozieTemplatingPlugin)
