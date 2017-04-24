package sbtoozie

import sbt.complete._
import sbt.complete.DefaultParsers._
import org.scalatest.{FlatSpec, Matchers}

class OozieParsersSpec extends FlatSpec with Matchers {

  import OozieParsers._

  behavior of "JobsFilter"

  it should "parse filters containing user key(s)" in {
    assertSuccess(JobsFilter, "user=foo")
    assertFailure(JobsFilter, "U=foo")
    assertFailure(JobsFilter, "User=foo")
  }

  it should "parse filters containing name key(s)" in {
    assertSuccess(JobsFilter, "name=foo")
    assertFailure(JobsFilter, "N=foo")
  }

  it should "parse filters containing group key(s)" in {
    assertSuccess(JobsFilter, "group=foo")
    assertFailure(JobsFilter, "G=foo")
  }

  it should "parse filters containing status key(s)" in {
    assertSuccess(JobsFilter, "status=foo")
    assertFailure(JobsFilter, "S=foo")
  }

  it should "parse filters containing frequency key(s)" in {
    assertSuccess(JobsFilter, "frequency=foo")
    assertFailure(JobsFilter, "F=foo")
  }

  it should "parse filters containing unit key(s)" in {
    assertSuccess(JobsFilter, "unit=foo")
    assertFailure(JobsFilter, "M=foo")
  }

  it should "parse filters containing start key(s)" in {
    assertSuccess(JobsFilter, "startcreatedtime=foo")
    assertFailure(JobsFilter, "SC=foo")
  }

  it should "parse filters containing end key(s)" in {
    assertSuccess(JobsFilter, "endcreatedtime=foo")
    assertFailure(JobsFilter, "EC=foo")
  }

  it should "parse filters containing multiple key=value pairs" in {
    assertSuccess(JobsFilter, "user=foo;name=bar;group=baz")
  }

  it should "parse and allow filters containing reused key=value pairs" in {
    assertSuccess(JobsFilter, "user=foo;name=bar;user=baz")
  }

  def assertSuccess[A](parser: Parser[A], input: String) = {
    val result = Parser.parse(" " + input, parser)
    result shouldBe 'isRight
    result.right.exists(_ == input) shouldBe true
  }

  def assertFailure[A](parser: Parser[A], input: String) = {
    val result = Parser.parse(" " + input, parser)
    result shouldBe 'isLeft
  }
}
