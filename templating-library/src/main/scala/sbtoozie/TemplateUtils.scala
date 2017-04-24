package sbtoozie

import java.io._
import java.nio.file.Path
import java.nio.charset.Charset

object TemplateUtils {

  def path(parts: String*): String = {
    parts.reduceLeft { (a: String, b: String) =>
      if (a.endsWith(File.separator) && b.startsWith(File.separator)) {
        a + b.substring(1, b.length)
      } else if (a.endsWith(File.separator) || b.startsWith(File.separator)) {
        a + b
      } else {
        a + File.separator + b
      }
    }
  }

  def valueOrErr(optionalValue: Option[String], errMsg: => String): String = {
    optionalValue match {
      case Some(value) => value
      case None => sys.error(errMsg)
    }
  }

  def write(file: File, content: String, charset: Charset = Charset.forName("UTF-8"), append: Boolean = false): Unit = {
    if (charset.newEncoder.canEncode(content)) {
      try {
        val parent = file.getParentFile
        if (parent != null) {
          parent.mkdirs()
        }
      } catch {
        case e: IOException => sys.error(s"Could not create parent directories for $file")
      }
      val fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, append), charset))
      try {
        fileWriter.write(content)
      } catch {
        case e: IOException => sys.error(s"error writing to $file: ${e.getMessage}")
      } finally {
        fileWriter.close()
      }
    } else {
      sys.error("string cannot be encoded by charset " + charset.name)
    }
  }

  def copyFile(sourceFile: File, targetFile: File): Unit = {
    require(sourceFile.exists, s"Source file ${sourceFile.getAbsolutePath} does not exist.")
    require(!sourceFile.isDirectory, s"Source file ${sourceFile.getAbsolutePath} is a directory.")
    val in = new FileInputStream(sourceFile).getChannel
    val out = new FileOutputStream(targetFile).getChannel
    // maximum bytes per transfer according to http://dzone.com/snippets/java-filecopy-using-nio
    val max = (64 * 1024 * 1024) - (32 * 1024)
    val total = in.size
    def loop(offset: Long): Long = {
      if (offset < total) {
        loop(offset + out.transferFrom(in, offset, max))
      } else {
        offset
      }
    }
    val copied = loop(0)
    if (copied != in.size) {
      sys.error(s"Could not copy $sourceFile to $targetFile ($copied/${in.size} bytes copied)")
    }
  }

  def copyFiles(source: File, destination: File, files: Seq[File]): Unit = {
    files.foreach { file =>
      val filePath = file.toPath
      val sourcePath = source.toPath
      val target = new File(destination, sourcePath.relativize(filePath).toString)
      val parent = new File(target.getParent)
      parent.mkdirs()
      copyFile(file, target)
    }
  }
}
