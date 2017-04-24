package sbtoozie

import java.io.File
import java.nio.file.Path
import scala.reflect.runtime.universe._
import org.slf4j.LoggerFactory
import play.twirl.api.BufferedContent
import sbtoozie.TemplateUtils._

object TemplateEvaluatorFacade {

  import TemplateEvaluator._

  def main(args: Array[String]): Unit = {
    val filePath = args(0)
    val templatesDirPath = args(1)
    val generatedDirPath = args(2)
    generateResources(
      new File(filePath),
      new File(templatesDirPath),
      new File(generatedDirPath)
    )
  }

  def generateResources(applicationDir: File, templatesDir: File, generatedDir: File): Unit = {
    val (templates, nonTemplates) = partitionTemplateFiles(applicationDir)
    templates.foreach { generate(_, templatesDir, generatedDir) }
    copyFiles(templatesDir, generatedDir, nonTemplates)
  }
}

object TemplateEvaluator {

  private val logger = LoggerFactory.getLogger(getClass)

  private val classLoader = getClass.getClassLoader

  private val mirror = runtimeMirror(classLoader)

  def generate(
    template: File,
    source: File,
    target: File
  ): Unit = {
    val templateClass = getTemplateClass(source, template)
    val moduleSymbol = mirror.moduleSymbol(templateClass)
    val moduleInstance = mirror.reflectModule(moduleSymbol).instance
    val classSymbol = mirror.classSymbol(templateClass)
    val methodName = reflect.runtime.universe.newTermName("apply")
    val method = classSymbol.toType.member(methodName).asMethod
    val methodMirror = mirror.reflect(moduleInstance).reflectMethod(method)
    val methodParamSymbols = method.paramss.flatten
    val methodParamTermSymbols = method.paramss.flatten.map(_.asTerm).toSet
    val defaultValuesByName: Map[String, Any] = {
      val defaultedMethods = classSymbol.toType.members.filter(_.name.decoded.startsWith("apply$default$"))
      val numDefaultParams = defaultedMethods.size
      val params = method.paramss.flatten
      method.paramss.flatten.zipWithIndex.drop(params.size - numDefaultParams).map {
        case (param, i) => (param, i + 1)
      }.map {
        case (param, i) =>
          val defaultMethodName = reflect.runtime.universe.newTermName("apply$default$" + i)
          val defaultMethodSymbol = classSymbol.toType.member(defaultMethodName)
          val defaultMethod = defaultMethodSymbol.asMethod
          val defaultMethodMirror = mirror.reflect(moduleInstance).reflectMethod(defaultMethod)
          (param.name.decoded, defaultMethodMirror.apply())
      }(scala.collection.breakOut)
    }
    val isNullaryMethod = methodParamSymbols.isEmpty
    val hasAllDefaultMethodArgs = !isNullaryMethod && defaultValuesByName.size == methodParamSymbols.size
    if (isNullaryMethod || hasAllDefaultMethodArgs) {
      val evaluatedTemplate = new File(target, removeTemplateExtension(getRelativePath(source, template).toString))
      val methodArgs = methodParamSymbols.flatMap { s =>
        defaultValuesByName.get(s.name.decoded)
      }
      val content = methodMirror(methodArgs: _*)
      if (content.isInstanceOf[BufferedContent[_]]) {
        val stringContent = content.asInstanceOf[BufferedContent[_]].body
        val prettyContent = stringContent.trim
        write(evaluatedTemplate, prettyContent)
      } else {
        write(evaluatedTemplate, content.toString)
      }
    }
  }

  @inline
  def getRelativePath(templatesDir: File, template: File): Path = {
    val templatesPath = templatesDir.toPath
    val templatePath = template.toPath
    templatesPath.relativize(templatePath)
  }

  /* /example/template.scala.ext -> example.ext.template$ */
  @inline
  def getTemplateClass(templatesDir: File, template: File): Class[_] = {
    val relativePath = getRelativePath(templatesDir, template)
    val relativeParentPath = relativePath.getParent
    val namespace = Option(relativeParentPath).map(_.toString.replace("/", "."))
    val unqualifiedClassName = s"${removeTemplateExtension(template.getName).split('.').reverse.mkString(".")}$$"
    val qualifiedClassName = namespace.fold(unqualifiedClassName)(_ + "." + unqualifiedClassName)
    logger.debug(s"Loading class $qualifiedClassName")
    classLoader.loadClass(qualifiedClassName)
  }

  @inline
  def removeTemplateExtension(path: String): String = {
    path.replace(".scala", "")
  }

  def partitionTemplateFiles(dir: File): (Array[File], Array[File]) = {
    def helper(dir: File): Array[File] = {
      val files = dir.listFiles
      files.filterNot(_.isDirectory) ++
      files.filter(_.isDirectory).flatMap(helper)
    }
    helper(dir).partition(_.getName.contains(".scala."))
  }
}
