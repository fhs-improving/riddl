/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Domain
import com.reactific.riddl.language.Messages.*

class HandlerValidatorTest extends ValidatingTest {

  "Handler Validation" should {
    "produce an error when on clause references a command that does not exist" in {
      val input =
        """
          |domain entityTest is {
          |context EntityContext is {
          |entity Hamburger is {
          |  state HamburgerState = {
          |    fields { field1: Number, field2: String }
          |    handler foo is {
          |      on command EntityCommand { example only { then set field1 to 445 } }
          |      on event EntityEvent { example only { then set field1 to 678 } }
          |    } described as "Irrelevant"
          |  } described as "Irrelevant"
          |} described as "Irrelevant"
          |} described as "Irrelevant"
          |} described as "Irrelevant"
          |""".stripMargin
      parseAndValidateDomain(input) { case (_: Domain, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          Error,
          "Path 'EntityCommand' was not resolved, in OnMessageClause" +
            " 'On command EntityCommand', but should refer to a command"
        )
        assertValidationMessage(
          msgs,
          Error,
          "Path 'EntityEvent' was not resolved, in OnMessageClause " +
            "'On event EntityEvent', but should refer to an event"
        )
      }
    }

    "produce an error when on clause references a message of the wrong type" in {
      val input =
        """
          |domain entityTest is {
          |context EntityContext is {
          |entity Hamburger is {
          |  state HamburgerState = { fields { field1: Number }
          |    handler foo is {
          |      on event Incoming { example only { then set field1 to 678 } }
          |    }
          |  }
          |}
          |}
          |}
          |""".stripMargin
      parseAndValidateDomain(input) { case (_, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          Error,
          "Path 'Incoming' was not resolved, in OnMessageClause " +
            "'On event Incoming', but should refer to an event"
        )
      }
    }

    "produce an error when on clause doesn't reference a message type" in {
      val input =
        """domain entityTest is {
          |context EntityContext is {
          |entity Hamburger is {
          |  type Incoming is String
          |  state HamburgerState = { fields { field1: Number }
          |    handler foo is {
          |      on event Incoming { example only { then set field1 to 678 } }
          |    }
          |  }
          |}
          |}
          |}
          |""".stripMargin
      parseAndValidateDomain(input) { case (_, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          Error,
          "Reference[Type] 'Incoming'(7:10) should reference an event but is a String type instead"
        )
      }
    }

    "produce an error when on clause references a state field that does not exist" in {
      val input = """
                    |domain entityTest is {
                    |context EntityContext is {
                    |entity Hamburger is {
                    |  state HamburgerState = { fields { field1: Number }
                    |    handler foo is {
                    |      on command EntityCommand { example only {
                    |        then set nonExistingField to 123
                    |      } }
                    |    }
                    |  }
                    |}
                    |}
                    |}
                    |""".stripMargin
      parseAndValidateDomain(input) { case (_, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          Error,
          "Path 'nonExistingField' was not resolved, in Example " +
            "'only', but should refer to a Field"
        )
      }
    }

    "produce an error when on clause sets state from a message field that does not exist" in {
      val input = """
                    |domain entityTest is {
                    |context EntityContext is {
                    |entity Hamburger is {
                    |  type EntityCommand is command { foo: Number }
                    |  state HamburgerState = { fields { field1: Number  }
                    |    handler foo is {
                    |      on command EntityCommand { example only {
                    |        then set field1 to @bar
                    |      } }
                    |    }
                    |  }
                    |}
                    |}
                    |}
                    |""".stripMargin
      parseAndValidateDomain(input) { case (_, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          Error,
          "Path 'bar' was not resolved, in Example 'only', but should refer to a Field"
        )
      }
    }

    "produce an error when on clause sets state from incompatible type of message field" in {
      val input = """
                    |domain entityTest is {
                    |context EntityContext is {
                    |entity Hamburger is {
                    |  type EntityCommand is command { foo: String }
                    |  state HamburgerState = {
                    |    fields { field1: Number }
                    |    handler doit is {
                    |      on command EntityCommand { example only {
                    |        then set field1 to @foo
                    |      } }
                    |    }
                    |  }
                    |}
                    |}
                    |}
                    |""".stripMargin
      parseAndValidateDomain(input) { case (_, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          Error,
          "assignment compatibility, but field:\n  field1"
        )
      }
    }
  }
}
