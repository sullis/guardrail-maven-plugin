package com.twilio.guardrail

import cats.data.NonEmptyList
import cats.implicits._
import com.twilio.swagger.core.StructuredLogger._
import com.twilio.swagger.core.{LogLevel, LogLevels}
import java.io.File
import org.apache.maven.plugin.{AbstractMojo, MojoExecutionException, MojoFailureException}
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import scala.collection.JavaConverters._
import scala.io.AnsiColor
import scala.language.higherKinds
import scala.util.control.NonFatal

class CodegenFailedException extends Exception

sealed abstract class Phase(val root: String)
object Main extends Phase("main")
object Test extends Phase("test")

abstract class AbstractGuardrailCodegenMojo(phase: Phase) extends AbstractMojo {
  @Parameter(defaultValue = "${project.build.directory}/generated-sources/guardrail-sources", property = "outputPath", required = true)
  def outputPath: File

  @Parameter(property = "language")
  var language: String = _

  @Parameter(property = "kind", defaultValue = "client")
  var kind: String = _

  @Parameter(property = "specPath", required = true)
  var specPath: File = _

  @Parameter(property = "packageName")
  var packageName: String = _

  @Parameter(property = "dtoPackage")
  var dtoPackage: String = _

  @Parameter(property = "tracing", defaultValue = "false")
  var tracing: Boolean = _

  @Parameter(property = "framework", defaultValue = "akka-http")
  var framework: String = _

  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  var project: MavenProject = _

  @Parameter(required = false, readonly = false)
  var customImports: java.util.List[_] = _

  protected def cli: CLICommon

  override def execute(): Unit = {
    if (!outputPath.exists()) {
      outputPath.mkdirs()
    }

    phase match {
      case Main => project.addCompileSourceRoot(outputPath.getAbsolutePath)
      case Test => project.addTestCompileSourceRoot(outputPath.getAbsolutePath)
    }


    try {
      val _language: String = Option(language).getOrElse({
        getLog.warn(s"[guardrail-maven-plugin] Default behaviour changing: Please specify <language>scala</language> to maintain the current settings. The default language will change to 'java' in a future release.")
        "scala"
      })
      val _kind: CodegenTarget = kind match {
        case "client" => CodegenTarget.Client
        case "server" => CodegenTarget.Server
        case "models" => CodegenTarget.Models
        case x => throw new MojoExecutionException(s"Unsupported codegen type: ${x}")
      }

      val arg = Args.empty.copy(
        kind=_kind,
        specPath=Some(specPath.getCanonicalPath()),
        packageName=Option(packageName).map(_.trim.split('.').toList),
        dtoPackage=Option(dtoPackage).toList.flatMap(_.split('.').filterNot(_.isEmpty).toList),
        context=Context.empty.copy(
          framework=Option(framework),
          tracing=Option(tracing).getOrElse(Context.empty.tracing)
        ),
        imports=Option(customImports).fold[List[String]](List.empty)(_.asScala.toList.map(_.toString))
      )

      val logLevel = Option(System.getProperty("guardrail.loglevel")).flatMap(LogLevels.apply).getOrElse(LogLevels.Warning)

      getLog.info(s"Generating ${_kind} from ${specPath.getName}")

      guardrailTask(List((_language, arg)), outputPath)(logLevel)
    } catch {
      case NonFatal(e) =>
        getLog.error("Failed to generate client", e)
        throw new MojoFailureException(s"Failed to generate client from '${specPath.getAbsolutePath}': $e", e)
    }
  }

  type Language = String
  def guardrailTask(tasks: List[(Language, Args)], sourceDir: java.io.File)(implicit logLevel: LogLevel): Seq[java.io.File] = {
    val preppedTasks: Map[String, NonEmptyList[Args]] = tasks.foldLeft(Map.empty[String, NonEmptyList[Args]]) { case (acc, (language, args)) =>
      val prepped = args.copy(outputPath=Some(sourceDir.getPath))
      acc.updated(language, acc.get(language).fold(NonEmptyList.one(prepped))(_ :+ prepped))
    }

    val (logger, paths) =
      cli.guardrailRunner
        .apply(preppedTasks)
        .fold[List[java.nio.file.Path]]({
          case MissingArg(args, Error.ArgName(arg)) =>
            getLog.error(s"Missing argument: ${AnsiColor.BOLD}${arg}${AnsiColor.RESET} (In block ${args})")
            throw new CodegenFailedException()
          case NoArgsSpecified =>
            List.empty
          case NoFramework =>
            getLog.error("No framework specified")
            throw new CodegenFailedException()
          case PrintHelp =>
            List.empty
          case UnknownArguments(args) =>
            getLog.error(s"Unknown arguments: ${args.mkString(" ")}")
            throw new CodegenFailedException()
          case UnparseableArgument(name, message) =>
            getLog.error(s"Unparseable argument ${name}: ${message}")
            throw new CodegenFailedException()
          case UnknownFramework(name) =>
            getLog.error(s"Unknown framework specified: ${name}")
            throw new CodegenFailedException()
          case RuntimeFailure(message) =>
            getLog.error(s"Error: ${message}")
            throw new CodegenFailedException()
          case UserError(message) =>
            getLog.error(s"Error: ${message}")
            throw new CodegenFailedException()
        }, identity)
        .runEmpty

    print(logger.show)

    paths.map(_.toFile).distinct
  }
}
