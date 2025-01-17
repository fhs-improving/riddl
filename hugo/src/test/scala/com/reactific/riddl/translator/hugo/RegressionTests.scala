/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.translator.hugo

import com.reactific.riddl.commands.CommandPlugin
import com.reactific.riddl.testkit.ParsingTest

/** Unit Tests For RegressionTests */
class RegressionTests extends ParsingTest {

  val regressionsFolder = "hugo/src/test/input/regressions/"
  val output = "hugo/target/regressions/"
  "Regressions" should {
    "not produce a MatchError" in {
      val source = "match-error.riddl"
      val args = Array(
        "hugo",
        "-o",
        output + "/match-error",
        "--with-statistics=true",
        "--with-glossary=true",
        "--with-todo-list=true",
        regressionsFolder + source
      )
      val result = CommandPlugin.runMain(args)
      result mustBe (0)
    }
  }
}
