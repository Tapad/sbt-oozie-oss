package com.tapad.oozie

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.channels.Channels
import java.util.Properties
import scala.collection.JavaConverters._

object ProtocolUtils {

  /* if key present in a and b, replace a value with b value */
  def mergeProperties(a: Properties, b: Properties): Properties = {
    a.putAll(b)
    a
  }

  def readPropertiesFromFile(file: File): Properties = {
    val fis = new FileInputStream(file)
    val props = new Properties
    props.load(fis)
    fis.close()
    props
  }

  def writePropertiesToFile(props: Properties, file: File) {
    val fos = new FileOutputStream(file)
    props.store(fos, null)
    fos.close()
  }

  /* if key present in a and b, replace a value with b value */
  def mergeXml(a: xml.Node, b: xml.Node): xml.Node = {
    val x = xmlconfig2properties(a)
    val y = xmlconfig2properties(b)
    x.putAll(y)
    properties2xmlconfig(x)
  }

  def readXmlFromFile(file: File): xml.Node = {
    xml.XML.loadFile(file)
  }

  def writeXmlToFile(node: xml.Node, file: File) {
    val encoding = "UTF-8"
    val fos = new FileOutputStream(file)
    val writer = Channels.newWriter(fos.getChannel, encoding)
    val printer = new xml.PrettyPrinter(80, 2)

    try {
      writer.write(s"""<?xml version="1.0" encoding="$encoding"?>\n""")
      writer.write(printer.format(node))
    } finally {
      writer.close()
    }
  }

  /* convert hadoop-style configuration document to properties */
  def xmlconfig2properties(node: xml.Node): Properties = {
    val properties = new Properties
    (node \\ "property").foreach { property =>
      val name = (property \ "name").text
      val value = (property \ "value").text
      properties.put(name, value)
    }
    properties
  }

  /* convert properties to hadoop-style configuration document */
  def properties2xmlconfig(props: Properties): xml.Node = {
    val entries = props.asScala.map { case (key, value) =>
      <property>
        <name>{key}</name>
        <value>{value}</value>
      </property>
    }
    <configuration>{entries}</configuration>
  }
}
