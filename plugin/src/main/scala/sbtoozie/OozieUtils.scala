package sbtoozie

import java.io.{File, IOException}
import java.nio.file.Files
import sbt._
import sbt.cli._
import org.apache.hadoop.fs.{FileSystem, Path}
import org.scalactic.{Or, Good, Bad, ErrorMessage}
import com.tapad.oozie.ProtocolUtils

object OozieUtils {

  val CoordinatorApplicationPathKey = "oozie.coord.application.path"

  def getFilesByName(dir: File): Map[String, File] = {
    val path = dir.toPath
    (PathFinder(dir) ***).get.filterNot(_ == dir).map { file =>
      path.relativize(file.toPath).toString -> file
    }(scala.collection.breakOut)
  }

  def getApplicationsByName(dir: File): Map[String, File] = {
    (PathFinder(dir) ** "workflow.xml").get.map { file =>
      val applicationDir = file.getParentFile
      val applicationName = getApplicationName(applicationDir, dir)
      applicationName -> applicationDir
    }(scala.collection.breakOut)
  }

  def getApplicationByName(applicationsByName: Map[String, File], name: String): File Or ErrorMessage = {
    Or.from(applicationsByName.get(name), orElse = s"No application exists that matches given name $name")
  }

  def getApplicationName(applicationDir: File, dir: File): String = {
    dir.toPath.relativize(applicationDir.toPath).toString
  }

  def getApplicationPropertiesFile(dir: File): File Or ErrorMessage = {
    val files = (PathFinder(dir) ** "*.properties").get
    if (files.isEmpty) {
      Bad(s"No properties file exists in $dir")
    } else if (files.size > 1) {
      Bad(s"Multiple properties files exist in $dir")
    } else {
      Good(files.head)
    }
  }

  def getValueFromPropertiesFile(file: File, key: String): String Or ErrorMessage = {
    val properties = ProtocolUtils.readPropertiesFromFile(file)
    val maybeValue = Option(properties.getProperty(key))
    Or.from(maybeValue, orElse = s"$file does not contain key $key")
  }

  def getCoordinatorApplicationPath(propertiesFile: File): String Or ErrorMessage = {
    getValueFromPropertiesFile(propertiesFile, CoordinatorApplicationPathKey)
  }

  /** Combine contents of directories, merging config-default.xml if present in both */
  def mergeDirectories(a: File, b: File, tmp: File): (Set[File], Set[File], Set[File]) = {
    val as = getFilesByName(a)
    val bs = getFilesByName(b)
    (as.toSeq ++ bs.toSeq).groupBy(_._1).foreach {
      case (name, group) if group.size == 1 =>
        val file = group.head._2
        val copy = Files.copy(file.toPath, tmp.toPath.resolve(file.getName))
        copy.toFile
      case (name, group) if name == "config-default.xml" =>
        val props = group.map(_._2)
          .map(ProtocolUtils.readXmlFromFile)
          .map(ProtocolUtils.xmlconfig2properties)
        val mergedProps = props.reduceLeft(ProtocolUtils.mergeProperties)
        val mergedXml = ProtocolUtils.properties2xmlconfig(mergedProps)
        val file = new File(tmp, name)
        file.createNewFile()
        ProtocolUtils.writeXmlToFile(mergedXml, file)
        file
      case (name, group) =>
        sys.error("Can not merge files: " + group.mkString(", "))
    }
    (as.values.toSet, bs.values.toSet, getFilesByName(tmp).values.toSet)
  }

  def uploadApplication(fs: FileSystem, resources: Map[File, Path], target: Path): Unit Or ErrorMessage = {
    // upload to tmp location
    val tmp = new Path(target.toString + "_COPYING_")
    // upload each file while printing progress
    applyWithProgressIndication(resources.toSeq: _*) { resource: (File, Path) =>
      val (file, path) = resource
      if (!file.isDirectory) {
        val localPath = new Path(file.getAbsolutePath)
        val remotePath = new Path(tmp, path)
        val remoteParent = remotePath.getParent
        if (!fs.exists(remoteParent)) {
          if (!fs.mkdirs(remoteParent)) {
            throw new IOException(s"Could not create parent $remoteParent for $remotePath")
          }
        }
        fs.copyFromLocalFile(false, true, localPath, remotePath)
      }
    } match {
      case Bad(message) =>
        fs.delete(tmp, true)
        Bad(message)
      case Good(_) =>
        // move tmp location to desired target
        try {
          if (fs.exists(target)) {
            fs.delete(target, true)
          }
          fs.rename(tmp, target)
          Good(())
        } catch {
          case e: Exception => Bad(s"Could not copy $tmp to $target: " + ErrorHandling.reducedToString(e))
        } finally {
          fs.delete(tmp, true)
        }
    }
  }
}
