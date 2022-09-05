package com.reactific.riddl

import com.reactific.riddl.commands.CommandOptions
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path

class CommonOptionsTest extends AnyWordSpec with Matchers {
  "RiddlOptions" should {
    "handle --suppress-warnings options" in {
      val args = Array("--suppress-warnings")
      val (common, _) = RiddlOptions.parseCommonOptions(args)
      common match {
        case Some(options) =>
          options.showWarnings mustBe false
          options.showStyleWarnings mustBe false
          options.showMissingWarnings mustBe false
        case None =>
          fail("Failed to parse options")
      }
    }

    "handle --suppress-style-warnings options" in {
      val args = Array("--suppress-style-warnings")
      val (common, _) = RiddlOptions.parseCommonOptions(args)
      common match {
        case Some(config) =>
          config.showWarnings mustBe true
          config.showStyleWarnings mustBe false
          config.showMissingWarnings mustBe true
        case None =>
          fail("Failed to parse options")
      }
    }

    "handle --suppress-missing-warnings options" in {
      val args = Array("--suppress-missing-warnings")
      val (common, _) = RiddlOptions.parseCommonOptions(args)
      common match {
        case Some(config) =>
          config.showWarnings mustBe true
          config.showStyleWarnings mustBe true
          config.showMissingWarnings mustBe false
        case None =>
          fail("Failed to parse options")
      }
    }

    "common options override properly" in {
      val optionFile = Path.of("riddlc/src/test/input/common-overrides.conf")
      CommandOptions.loadCommonOptions(optionFile) match {
        case Left(messages) =>
          fail(messages.format)
        case Right(opts) =>
          opts.showWarnings mustBe true
          opts.showStyleWarnings mustBe true
          opts.showMissingWarnings mustBe true
      }
    }

    "empty args are eliminated" in {
      val opts = Array("--show-times", "parse", "", " -i", "  ", "file.riddl")
      val (comm, remaining) = RiddlOptions.parseCommonOptions(opts)
      comm match {
        case Some(options) =>
          options.showTimes must be(true)
          RiddlOptions.parseCommandOptions(remaining) match {
            case Right(options) =>
              options.inputFile mustBe Some(Path.of("file.riddl"))
            case Left(messages) =>
              fail(messages.format)
          }
        case _ =>
          fail("Failed to parse options")
      }
    }

  }
}