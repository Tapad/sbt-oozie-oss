package sbtoozie

import sbt._
import sbt.complete._
import sbt.complete.DefaultParsers._

object OozieParsers {

  val SpaceComplete: Parser[String] = token(Space.map(_.toString))

  val UserFilterKey: Parser[String] = tabComplete("user")

  val NameFilterKey: Parser[String] = tabComplete("name")

  val GroupFilterKey: Parser[String] = tabComplete("group")

  val StatusFilterKey: Parser[String] = tabComplete("status")

  val FrequencyFilterKey: Parser[String] = tabComplete("frequency")

  val UnitFilterKey: Parser[String] = tabComplete("unit")

  val StartFilterKey: Parser[String] = tabComplete("startcreatedtime")

  val EndFilterKey: Parser[String] = tabComplete("endcreatedtime")

  val JobsFilter: Parser[String] = {
    val keys = Seq(
      UserFilterKey,
      NameFilterKey,
      GroupFilterKey,
      StatusFilterKey,
      FrequencyFilterKey,
      UnitFilterKey,
      StartFilterKey,
      EndFilterKey
    )
    val filterStringParser = SpaceComplete ~> repsep(
      keys.map(_ ~ literal("=") ~ NotQuoted).reduce(_ | _),
      literal(";")
    ).map(
      _.map {
        case ((key, operator), value) => key + operator + value
      }.mkString(";")
    )
    filterStringParser.?.map {
      case Some(value) => value
      case None => ""
    }
  }

  val CoordinatorJobId: Parser[String] = {
    SpaceComplete ~> NotQuoted.filter(
      _.endsWith("C"),
      msg = (jobId: String) => s"Invalid coordinator ID: $jobId. The given job ID must end with '-C'"
    )
  }

  val WorkflowJobId: Parser[String] = {
    SpaceComplete ~> NotQuoted.filter(
      _.endsWith("W"),
      msg = (jobId: String) => s"Invalid workflow ID: $jobId. The given job ID must end with '-W'"
    )
  }

  val RerunTypeAndScope: Parser[(String, String)] = {
    val key = tabComplete("-") ~> (tabComplete("action") | tabComplete("date"))
    ((SpaceComplete ~> key <~ SpaceComplete) ~ NotQuoted)
  }

  val IgnoreScope: Parser[String] = {
    ((SpaceComplete ~> tabComplete("-action") <~ SpaceComplete) ~> NotQuoted)
  }

  val ChangeValue: Parser[String] = {
    ((SpaceComplete ~> tabComplete("-value") <~ SpaceComplete) ~> NotQuoted)
  }

  private def tabComplete(input: String): Parser[String] = {
    token(literal(input))
  }
}
