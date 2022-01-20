package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.Identifier
import com.yoppworks.ossum.riddl.language.AST.Strng
import com.yoppworks.ossum.riddl.language.AST.Type
import com.yoppworks.ossum.riddl.language.parsing.RiddlParserInput

/** Unit Tests For Includes */
class IncludeAndImportTest extends ParsingTest {

  "Include" should {
    "handle missing files" in {
      parseDomainDefinition(
        RiddlParserInput("domain foo is { include \"unexisting\" } explained as \"foo\""),
        identity
      ) match {
        case Right(_) => fail("Should have gotten 'does not exist' error")
        case Left(errors) =>
          errors.size must be(1)
          errors.exists(_.format.contains("does not exist,"))
      }
    }
    "handle inclusions into domain" in {
      val domain = checkFile("Domain Includes", "domainIncludes.riddl")
      domain.contents.head.types.head mustBe Type(
        (1, 1, "domainIncluded.riddl"),
        Identifier((1, 6, "domainIncluded.riddl"), "foo"),
        Strng((1, 13, "domainIncluded.riddl")),
        None
      )
    }
    "handle inclusions into contexts" in {
      val domain = checkFile("Context Includes", "contextIncludes.riddl")
      domain.contents.head.contexts.head.types.head mustBe Type(
        (1, 1, "contextIncluded.riddl"),
        Identifier((1, 6, "contextIncluded.riddl"), "foo"),
        Strng((1, 12, "contextIncluded.riddl")),
        None
      )
    }
  }

  "Import" should {
    "work syntactically" in {
      val root = checkFile("Import", "import.riddl")
      root.domains must not(be(empty))
      root.domains.head.domains must not(be(empty))
      root.domains.head.domains.head.id.value must be("NotImplemented")
    }
    "handle missing files" in {
      val input = "domain foo is { import domain foo from \"nonexisting\" } described as \"foo\""
      parseDomainDefinition(RiddlParserInput(input), identity) match {
        case Right(_) => fail("Should have gotten 'does not exist' error")
        case Left(errors) =>
          errors.size must be(1)
          errors.exists(_.format.contains("does not exist,"))
      }
    }
  }
}