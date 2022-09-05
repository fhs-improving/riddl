package com.reactific.riddl.commands

import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.utils.Logger
import pureconfig.{ConfigCursor, ConfigReader}
import scopt.OParser

import java.nio.file.Path

object TestCommand {
  case class Options(
    command: Command = PluginCommand("test"),
    arg1: String = "",
  ) extends CommandOptions {
    override def inputFile: Option[Path] = None
  }
}

/** A pluggable command for testing plugin commands! */
class TestCommand extends CommandPlugin[TestCommand.Options]("test") {
  import TestCommand.Options
  override def getOptions(log : Logger): (OParser[Unit, Options], Options) = {
    val builder = OParser.builder[Options]
    import builder.*
    OParser.sequence(
      cmd("test")
        .children(
          arg[String]("arg1").action( (s,to)=>
            to.copy(arg1 = s))
            .validate { a1 =>
              if (a1.nonEmpty) { Right(()) }
              else { Left("All argument keys must be nonempty") }
            }
        )
    ) -> Options()
  }

  override def getConfigReader(
    log: Logger
  ): ConfigReader[Options] = { (cur: ConfigCursor) =>
    for {
      objCur <- cur.asObjectCursor
      contentCur <- objCur.atKey("test")
      contentObjCur <- contentCur.asObjectCursor
      arg1Res <- contentObjCur.atKey("arg1")
      str <- arg1Res.asString
    } yield {
      Options(arg1 = str)
    }
  }

  override def run(
    options: Options,
    commonOptions: CommonOptions,
    log: Logger
  ): Either[Messages, Unit] = {
    println(s"arg1: '${options.arg1}''")
    Right(())
  }
}