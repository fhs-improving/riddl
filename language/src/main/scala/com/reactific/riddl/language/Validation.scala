/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.ast.Location

import java.util.regex.PatternSyntaxException
import scala.reflect.ClassTag
import scala.reflect.classTag
import scala.util.control.NonFatal
import scala.util.matching.Regex
import org.apache.commons.lang3.exception.ExceptionUtils

import scala.annotation.tailrec
import scala.annotation.unused
import scala.collection.mutable

/** Validates an AST */
object Validation {

  def validate(
    root: Definition,
    commonOptions: CommonOptions = CommonOptions()
  ): Messages.Messages = {
    val symTab = SymbolTable(root)
    val state = ValidationState(symTab, root, commonOptions)
    val parents = mutable.Stack.empty[Definition]
    val result =
      try { state.validateDefinitions(root, parents).checkOverloads(symTab) }
      catch {
        case NonFatal(xcptn) =>
          val message = ExceptionUtils.getRootCauseStackTrace(xcptn)
            .mkString("\n")
          state.addSevere(Location.empty, message)
      }
    result.messages.sortBy(_.loc)
  }

  case class ValidationState(
    symbolTable: SymbolTable,
    root: Definition = RootContainer.empty,
    commonOptions: CommonOptions = CommonOptions())
      extends Folding.PathResolutionState[ValidationState] {

    def validateDefinitions(
      definition: Definition,
      parents: mutable.Stack[Definition]
    ): ValidationState = {
      // Capture current parse state including now the definition as the
      // top element of the parent stack
      definition match {
        case leaf: LeafDefinition => validateADefinition(leaf, parents.toSeq)
        case container: Definition =>
          validateADefinition(container, parents.toSeq)
          parents.push(container)
          val st = container.contents.foldLeft(this) { (st, defn) =>
            st.validateDefinitions(defn, parents)
          }
          parents.pop()
          st
      }
    }

    def validateADefinition(
      definition: Definition,
      parents: Seq[Definition]
    ): ValidationState = {
      setParents(definition +: parents)
      definition match {
        case leaf: LeafDefinition => leaf match {
            case f: Field        => validateField(f, parents)
            case e: Example      => validateExample(e, parents)
            case e: Enumerator   => validateEnumerator(e, parents)
            case i: Invariant    => validateInvariant(i, parents)
            case t: Term         => validateTerm(t, parents)
            case p: Pipe         => validatePipe(p, parents)
            case i: Inlet        => validateInlet(i, parents)
            case o: Outlet       => validateOutlet(o, parents)
            case ij: InletJoint  => validateInletJoint(ij, parents)
            case oj: OutletJoint => validateOutletJoint(oj, parents)
            case ai: Author      => validateAuthorInfo(ai, parents)
            case sa: StoryActor  => validateStoryActor(sa, parents)
            case sc: StoryCase   => validateStoryCase(sc, parents)
          }
        case ed: EntityDefinition => ed match {
            case t: Type      => validateType(t, parents)
            case s: State     => validateState(s, parents)
            case h: Handler   => validateHandler(h, parents)
            case f: Function  => validateFunction(f, parents)
            case i: Invariant => validateInvariant(i, parents)
            case i: Include[Definition] @unchecked => validateInclude(i)
          }
        case cd: ContextDefinition => cd match {
            case t: Type         => validateType(t, parents)
            case h: Handler      => validateHandler(h, parents)
            case f: Function     => validateFunction(f, parents)
            case e: Entity       => validateEntity(e, parents)
            case a: Adaptor      => validateAdaptor(a, parents)
            case p: Processor    => validateProcessor(p, parents)
            case p: Projection   => validateProjection(p, parents)
            case t: Term         => validateTerm(t, parents)
            case p: Pipe         => validatePipe(p, parents)
            case ij: InletJoint  => validateInletJoint(ij, parents)
            case oj: OutletJoint => validateOutletJoint(oj, parents)
            case s: Saga         => validateSaga(s, parents)
            case i: Include[Definition] @unchecked => validateInclude(i)
          }
        case dd: DomainDefinition => dd match {
            case t: Type    => validateType(t, parents)
            case c: Context => validateContext(c, parents)
            case d: Domain  => validateDomain(d, parents)
            case s: Story   => validateStory(s, parents)
            case p: Plant   => validatePlant(p, parents)
            case t: Term    => validateTerm(t, parents)
            case ai: Author => validateAuthorInfo(ai, parents)
            case i: Include[Definition] @unchecked => validateInclude(i)
          }
        case hd: HandlerDefinition =>
          hd match { case oc: OnClause => validateOnClause(oc, parents) }
        case ad: AdaptorDefinition => ad match {
            case i: Include[Definition] @unchecked => validateInclude(i)
            case a: Adaptation => validateAdaptation(a, parents)
          }
        case ss: SagaStep     => validateSagaStep(ss, parents)
        case _: RootContainer => this // ignore
      }
    }

    def validateTerm(t: Term, parents: Seq[Definition]): ValidationState = {
      this.checkDefinition(parents.head, t).checkDescription(t)
    }

    def validateEnumerator(
      e: Enumerator,
      parents: Seq[Definition]
    ): ValidationState = {
      this.checkDefinition(parents.head, e).checkDescription(e)
    }

    def validateField(f: Field, parents: Seq[Definition]): ValidationState = {
      checkDefinition(parents.head, f)
        .addIf(f.id.value.matches("^[^a-z].*"))(Message(
          f.id.loc,
          "Field names should begin with a lower case letter",
          StyleWarning
        )).checkTypeExpression(f.typeEx, f).checkDescription(f)
    }

    def validateExample(
      e: Example,
      parents: Seq[Definition]
    ): ValidationState = {
      checkDefinition(parents.head, e).checkExample(e).checkDescription(e)
    }

    def validateInvariant(
      i: Invariant,
      parents: Seq[Definition]
    ): ValidationState = {
      checkDefinition(parents.head, i)
        .checkOption(i.expression, "condition", i) { (st, expr) =>
          st.checkExpression(expr, i)
        }.checkDescription(i)
    }

    def validatePipe(p: Pipe, parents: Seq[Definition]): ValidationState = {
      checkDefinition(parents.head, p)
        .checkOption(p.transmitType, "transmit type", p) { (st, typeRef) =>
          st.checkPathRef[Type](typeRef.id, p)()()
        }.checkDescription(p)
    }

    def validateInlet(i: Inlet, parents: Seq[Definition]): ValidationState = {
      checkDefinition(parents.head, i).checkRef[Type](i.type_, i)
        .checkOption(i.entity, "entity reference", i) { (st, er) =>
          st.checkRef[Entity](er, i)
        }.checkDescription(i)
    }

    def validateOutlet(o: Outlet, parents: Seq[Definition]): ValidationState = {
      checkDefinition(parents.head, o).checkRef[Type](o.type_, o)
        .checkOption(o.entity, "entity reference", o) { (st, er) =>
          st.checkRef[Entity](er, o)
        }.checkDescription(o)
    }

    def validateInletJoint(
      ij: InletJoint,
      parents: Seq[Definition]
    ): ValidationState = {
      checkDefinition(parents.head, ij).checkPathRef[Pipe](ij.pipe.id, ij)()()
        .checkPathRef[Inlet](ij.inletRef.id, ij)()().checkDescription(ij)
    }

    def validateOutletJoint(
      oj: OutletJoint,
      parents: Seq[Definition]
    ): ValidationState = {
      checkDefinition(parents.head, oj).checkPathRef[Pipe](oj.pipe.id, oj)()()
        .checkPathRef[Outlet](oj.outletRef.id, oj)()().checkDescription(oj)
    }

    def validateAuthorInfo(
      ai: Author,
      parents: Seq[Definition]
    ): ValidationState = {
      checkDefinition(parents.head, ai)
        .checkNonEmptyValue(ai.name, "name", ai, Error, required = true)
        .checkNonEmptyValue(ai.email, "email", ai, Error, required = true)
        .checkDescription(ai)
    }

    def validateType(t: Type, parents: Seq[Definition]): ValidationState = {
      checkDefinition(parents.head, t).check(
        t.id.value.head.isUpper,
        s"${t.identify} should start with a capital letter",
        StyleWarning,
        t.loc
      ).checkIf(!t.typ.isInstanceOf[AggregateTypeExpression]) { vs =>
        vs.setParents(t +: parents)
        vs.checkTypeExpression(t.typ, t)
        vs.setParents(parents)
      }.checkDescription(t)
    }

    def validateState(s: State, parents: Seq[Definition]): ValidationState = {
      checkDefinition(parents.head, s).checkContainer(parents.headOption, s)
        .addIf(s.aggregation.fields.isEmpty && !s.isEmpty) {
          Message(
            s.aggregation.loc,
            s"${s.identify} must define at least one field"
          )
        }.addIf(s.handlers.isEmpty && !s.isEmpty) {
          Message(s.loc, s"${s.identify} must define a handler")
        }.checkDescription(s)
    }

    def validateFunction(
      f: Function,
      parents: Seq[Definition]
    ): ValidationState = {
      checkContainer(parents.headOption, f).checkDescription(f)
    }

    def validateHandler(
      h: Handler,
      parents: Seq[Definition]
    ): ValidationState = {
      checkContainer(parents.headOption, h).checkDescription(h)
    }

    def validateOnClause(
      oc: OnClause,
      parents: Seq[Definition]
    ): ValidationState = {
      checkIf(oc.msg.nonEmpty) { st =>
        st.setParents(oc +: parents)
          .checkMessageRef(oc.msg, oc, oc.msg.messageKind).setParents(parents)
      }.checkDescription(oc)
    }

    def validateInclude[T <: Definition](i: Include[T]): ValidationState = {
      check(i.nonEmpty, "Include has no included content", Error, i.loc)
        .check(i.path.nonEmpty, "Include has no path provided", Error, i.loc)
        .step { s =>
          if (i.path.nonEmpty) {
            s.check(
              i.path.get.toString.nonEmpty,
              "Include path provided is empty",
              Error,
              i.loc
            )
          } else { s }
        }
    }

    def validateEntity(e: Entity, parents: Seq[Definition]): ValidationState = {
      checkContainer(parents.headOption, e).checkOptions[EntityOption](
        e.options,
        e.loc
      ).addIf(e.states.isEmpty && !e.isEmpty) {
        Message(
          e.loc,
          s"${e.identify} must define at least one state",
          MissingWarning
        )
      }.addIf(e.handlers.nonEmpty && e.handlers.forall(_.clauses.isEmpty)) {
        Message(e.loc, s"${e.identify} has only empty handlers", MissingWarning)
      }.addIf(e.hasOption[EntityIsFiniteStateMachine] && e.states.sizeIs < 2) {
        Message(
          e.loc,
          s"${e.identify} is declared as an fsm, but doesn't have at least two states",
          Error
        )
      }.checkDescription(e)
    }

    def validateProjection(
      p: Projection,
      parents: Seq[Definition]
    ): ValidationState = {
      checkContainer(parents.headOption, p).checkDescription(p)
    }

    def validateAdaptor(
      a: Adaptor,
      parents: Seq[Definition]
    ): ValidationState = {
      checkContainer(parents.headOption, a).checkDescription(a)
    }

    def validateAdaptation(
      a: Adaptation,
      parents: Seq[Definition]
    ): ValidationState = {
      val maybeParent = parents.headOption
      checkContainer(maybeParent, a).step { st =>
        val parent = maybeParent.getOrElse(RootContainer.empty)
        a match {
          case cca: CommandCommandA8n => st
              .checkPathRef[Command](cca.messageRef.id, a)()()
              .checkPathRef[Command](cca.command.id, a)()()
          case eca: EventCommandA8n => st.checkDefinition(parent, eca)
              .checkPathRef[Event](eca.messageRef.id, a)()()
              .checkPathRef[Command](eca.command.id, a)()()
          case eaa: EventActionA8n => st.checkDefinition(parent, eaa)
              .checkPathRef[Event](eaa.messageRef.id, a)()()
              .checkActions(eaa.actions, a)
        }
      }.checkDescription(a)
    }

    def validateProcessor(
      p: Processor,
      parents: Seq[Definition]
    ): ValidationState = {
      checkContainer(parents.headOption, p).checkProcessorKind(p)
        .checkDescription(p)
    }

    def validateDomain(d: Domain, parents: Seq[Definition]): ValidationState = {
      checkContainer(parents.headOption, d).checkDescription(d)
    }

    def validateSaga(s: Saga, parents: Seq[Definition]): ValidationState = {
      checkContainer(parents.headOption, s).checkDescription(s)
    }

    def validateSagaStep(
      s: SagaStep,
      parents: Seq[Definition]
    ): ValidationState = {
      checkContainer(parents.headOption, s).check(
        s.doAction.getClass == s.undoAction.getClass,
        "The primary action and revert action must be the same shape",
        Error,
        s.doAction.loc
      ).checkDescription(s)
    }

    def validateContext(
      c: Context,
      parents: Seq[Definition]
    ): ValidationState = {
      checkContainer(parents.headOption, c)
        .checkOptions[ContextOption](c.options, c.loc).checkDescription(c)
    }

    def validateStory(
      s: Story,
      parents: Seq[Definition]
    ): ValidationState = {
      checkContainer(parents.headOption, s).checkIf(s.userStory.isEmpty) {
        vs: ValidationState =>
          vs.addMissing(s.loc, s"${s.identify} is missing a user story")
      }.checkExamples(s.examples).checkDescription(s)
    }

    def validateStoryActor(
      @unused sa: StoryActor,
      @unused parents: Seq[Definition]
    ): ValidationState = {
      checkDefinition(sa, parents.head).checkIf(sa.is_a.isEmpty) { vs =>
        vs.addMissing(sa.loc, s"${sa.identify} is missing its kind")
      }.checkDescription(sa)
      this
    }

    def validateStoryCase(
      @unused sc: StoryCase,
      @unused parents: Seq[Definition]
    ): ValidationState = {
      checkDefinition(sc, parents.head).stepIf(sc.nonEmpty) { vs =>
        vs.checkIf(sc.title.isEmpty)(
          _.addMissing(sc.loc, s"${sc.identify} is missing a title")
        )
        vs.checkIf(sc.uses.isEmpty)(
          _.addMissing(sc.loc, s"${sc.identify} doesn't use any definitions")
        )
        vs.checkIf(sc.scope.isEmpty)(
          _.addError(sc.loc, s"${sc.identify} does not define a scope")
        )
        vs.checkIf(sc.interactions.isEmpty)(_.addMissing(
          sc.loc,
          s"${sc.identify} doesn't define any interactions"
        ))
      }.checkDescription(sc)
    }

    def validatePlant(
      p: Plant,
      parents: Seq[Definition]
    ): ValidationState = {
      checkContainer(parents.headOption, p).checkDescription(p)
    }

    def checkOverloads(
      symbolTable: SymbolTable
    ): ValidationState = {
      symbolTable.foreachOverloadedSymbol { defs: Seq[Seq[Definition]] =>
        this.checkSequence(defs) { (s, defs2) =>
          if (defs2.sizeIs == 2) {
            val first = defs2.head
            val last = defs2.last
            s.addStyle(
              last.loc,
              s"${last.identify} overloads ${first.identifyWithLoc}"
            )
          } else if (defs2.sizeIs > 2) {
            val first = defs2.head
            val tail = defs2.tail.map(d => d.identifyWithLoc).mkString(s",\n  ")
            s.addStyle(first.loc, s"${first.identify} overloads:\n  $tail")
          } else { s }
        }
      }
    }

    def parentOf(
      definition: Definition
    ): Container[Definition] = {
      symbolTable.parentOf(definition).getOrElse(RootContainer.empty)
    }

    def lookup[T <: Definition: ClassTag](
      id: Seq[String]
    ): List[T] = { symbolTable.lookup[T](id) }

    def addIf(predicate: Boolean)(msg: => Message): ValidationState = {
      if (predicate) add(msg) else this
    }

    private val vowels: Regex = "[aAeEiIoOuU]".r

    def article(thing: String): String = {
      val article = if (vowels.matches(thing.substring(0, 1))) "an" else "a"
      s"$article $thing"
    }

    def check(
      predicate: Boolean = true,
      message: => String,
      kind: KindOfMessage,
      loc: Location
    ): ValidationState = {
      if (!predicate) { add(Message(loc, message, kind)) }
      else { this }
    }

    def checkIf(
      predicate: Boolean = true
    )(f: ValidationState => ValidationState
    ): ValidationState = {
      if (predicate) { f(this) }
      else { this }
    }

    def checkIdentifierLength[T <: Definition](
      d: T,
      min: Int = 3
    ): ValidationState = {
      if (d.id.value.nonEmpty && d.id.value.length < min) {
        addStyle(
          d.id.loc,
          s"${d.kind} identifier '${d.id.value}' is too short. The minimum length is $min"
        )
      } else { this }
    }

    def checkPattern(p: Pattern): ValidationState = {
      try {
        val compound = p.pattern.map(_.s).reduce(_ + _)
        java.util.regex.Pattern.compile(compound)
        this
      } catch {
        case x: PatternSyntaxException => add(Message(p.loc, x.getMessage))
      }
    }

    def checkEnumeration(
      enumerators: Seq[Enumerator]
    ): ValidationState = {
      checkSequence(enumerators) { case (state, enumerator) =>
        val id = enumerator.id
        state.checkIdentifierLength(enumerator).check(
          id.value.head.isUpper,
          s"Enumerator '${id.format}' must start with upper case",
          StyleWarning,
          id.loc
        ).checkDescription(enumerator)
      }
    }

    def checkAlternation(
      alternation: AST.Alternation,
      typeDef: Definition
    ): ValidationState = {
      checkSequence(alternation.of) { case (state, typex) =>
        state.checkTypeExpression(typex, typeDef)
      }
    }

    def checkRangeType(
      rt: RangeType
    ): ValidationState = {
      this.check(
        rt.min >= BigInt.long2bigInt(Long.MinValue),
        "Minimum value might be too small to store in a Long",
        Warning,
        rt.loc
      ).check(
        rt.max <= BigInt.long2bigInt(Long.MaxValue),
        "Maximum value might be too large to store in a Long",
        Warning,
        rt.loc
      )
    }

    def checkAggregation(
      agg: Aggregation,
      definition: Definition
    ): ValidationState = {
      checkSequence(agg.fields) { case (state, field) =>
        state.checkIdentifierLength(field).check(
          field.id.value.head.isLower,
          "Field names in aggregates should start with a lower case letter",
          StyleWarning,
          field.loc
        ).checkTypeExpression(field.typeEx, definition).checkDescription(field)
      }
    }

    def checkMessageType(
      mt: MessageType,
      typeDef: Definition
    ): ValidationState = {
      val kind = mt.messageKind.kind
      checkSequence(mt.fields) { case (state, field) =>
        state.checkIdentifierLength(field).check(
          field.id.value.head.isLower,
          s"Field names in $kind messages should start with a lower case letter",
          StyleWarning,
          field.loc
        ).checkTypeExpression(field.typeEx, typeDef).checkDescription(field)
      }
    }

    def checkMapping(
      mapping: AST.Mapping,
      typeDef: Definition
    ): ValidationState = {
      this.checkTypeExpression(mapping.from, typeDef)
        .checkTypeExpression(mapping.to, typeDef)
    }

    def checkTypeExpression[TD <: Definition](
      typ: TypeExpression,
      defn: Definition
    ): ValidationState = {
      typ match {
        case AliasedTypeExpression(_, id: PathIdentifier) =>
          checkPathRef[Type](id, defn)()()
        case agg: Aggregation            => checkAggregation(agg, defn)
        case mt: MessageType             => checkMessageType(mt, defn)
        case alt: Alternation            => checkAlternation(alt, defn)
        case mapping: Mapping            => checkMapping(mapping, defn)
        case rt: RangeType               => checkRangeType(rt)
        case p: Pattern                  => checkPattern(p)
        case Enumeration(_, enumerators) => checkEnumeration(enumerators)
        case Optional(_, tye)            => checkTypeExpression(tye, defn)
        case OneOrMore(_, tye)           => checkTypeExpression(tye, defn)
        case ZeroOrMore(_, tye)          => checkTypeExpression(tye, defn)
        case SpecificRange(_, typex: TypeExpression, min, max) =>
          checkTypeExpression(typex, defn)
          check(
            min >= 0,
            "Minimum cardinality must be non-negative",
            Error,
            typ.loc
          )
          check(
            max >= 0,
            "Maximum cardinality must be non-negative",
            Error,
            typ.loc
          )
          check(
            min < max,
            "Minimum cardinality must be less than maximum cardinality",
            Error,
            typ.loc
          )
        case UniqueId(_, pid) => checkPathRef[Entity](pid, defn)()()
        case EntityReferenceTypeExpression(_, pid) =>
          checkPathRef[Entity](pid, defn)()()
        case _: PredefinedType => this // nothing needed
        case _: TypeRef        => this // handled elsewhere
        case x =>
          require(requirement = false, s"Failed to match definition $x")
          this
      }
    }

    private type SingleMatchValidationFunction = (
      /* state:*/ ValidationState,
      /* expectedClass:*/ Class[?],
      /* pathIdSought:*/ PathIdentifier,
      /* foundClass*/ Class[? <: Definition],
      /* definitionFound*/ Definition
    ) => ValidationState

    private type MultiMatchValidationFunction = (
      /* state:*/ ValidationState,
      /* pid: */ PathIdentifier,
      /* list: */ List[(Definition, Seq[Definition])]
    ) => Seq[Definition]

    private val nullSingleMatchingValidationFunction
      : SingleMatchValidationFunction = (state, _, _, _, _) => { state }

    private def defaultSingleMatchValidationFunction(
      state: ValidationState,
      expectedClass: Class[?],
      pid: PathIdentifier,
      foundClass: Class[? <: Definition],
      @unused definitionFound: Definition
    ): ValidationState = {
      state.check(
        expectedClass.isAssignableFrom(foundClass),
        s"'${pid.format}' was expected to be ${article(expectedClass.getSimpleName)} but is " +
          s"${article(foundClass.getSimpleName)}.",
        Error,
        pid.loc
      )
    }

    private def defaultMultiMatchValidationFunction[T <: Definition: ClassTag](
      state: ValidationState,
      pid: PathIdentifier,
      list: List[(Definition, Seq[Definition])]
    ): Seq[Definition] = {
      // Extract all the definitions that were found
      val definitions = list.map(_._1)
      val allDifferent = definitions.map(_.kind).distinct.sizeIs ==
        definitions.size
      val expectedClass = classTag[T].runtimeClass
      if (allDifferent || definitions.head.isImplicit) {
        // pick the one that is the right type or the first one
        list.find(_._1.getClass == expectedClass) match {
          case Some((defn, parents)) => defn +: parents
          case None                  => list.head._1 +: list.head._2
        }
      } else {
        state.addError(
          pid.loc,
          s"""Path reference '${pid.format}' is ambiguous. Definitions are:
             |${formatDefinitions(list)}""".stripMargin
        )
        Seq.empty[Definition]
      }
    }

    private def formatDefinitions[T <: Definition](
      list: List[(T, SymbolTable#Parents)]
    ): String = {
      list.map { case (definition, parents) =>
        "  " + parents.reverse.map(_.id.value).mkString(".") + "." +
          definition.id.value + " (" + definition.loc + ")"
      }.mkString("\n")
    }

    private def notResolved[T <: Definition: ClassTag](
      pid: PathIdentifier,
      container: Definition,
      kind: Option[String]
    ): Unit = {
      val tc = classTag[T].runtimeClass
      val message = s"Path '${pid.format}' was not resolved," +
        s" in ${container.identify}"
      val referTo = if (kind.nonEmpty) kind.get else tc.getSimpleName
      addError(
        pid.loc,
        message + {
          if (referTo.nonEmpty) s", but should refer to ${article(referTo)}"
          else ""
        }
      )
    }

    def checkPathRef[T <: Definition: ClassTag](
      pid: PathIdentifier,
      container: Definition,
      kind: Option[String] = None
    )(single: SingleMatchValidationFunction =
        defaultSingleMatchValidationFunction
    )(multi: MultiMatchValidationFunction = defaultMultiMatchValidationFunction
    ): ValidationState = {
      val tc = classTag[T].runtimeClass
      if (pid.value.isEmpty) {
        val message =
          s"An empty path cannot be resolved to ${article(tc.getSimpleName)}"
        addError(pid.loc, message)
      } else {
        val result = resolvePath(pid) { definitions =>
          if (definitions.nonEmpty) {
            val d = definitions.head
            single(this, tc, pid, d.getClass, d)
            definitions
          } else { definitions }
        } { list => multi(this, pid, list) }
        if (result.isEmpty) { notResolved(pid, container, kind) }
      }
      this
    }

    def checkRef[T <: Definition: ClassTag](
      reference: Reference[T],
      defn: Definition,
      kind: Option[String] = None
    ): ValidationState = { checkPathRef[T](reference.id, defn, kind)()() }

    def checkMessageRef(
      ref: MessageRef,
      topDef: Definition,
      kind: MessageKind
    ): ValidationState = {
      if (ref.isEmpty) { addError(ref.id.loc, s"${ref.identify} is empty") }
      else {
        checkPathRef[Type](ref.id, topDef, Some(kind.kind)) {
          (state, _, _, _, defn) =>
            defn match {
              case Type(_, _, typ, _, _) => typ match {
                  case MessageType(_, mk, _) => state.check(
                      mk == kind,
                      s"'${ref.identify} should be ${article(kind.kind)} type" +
                        s" but is ${article(mk.kind)} type instead",
                      Error,
                      ref.id.loc
                    )
                  case te: TypeExpression => state.addError(
                      ref.id.loc,
                      s"'${ref.identify} should reference ${article(kind.kind)} but is a ${AST.kind(te)} type instead"
                    )
                }
              case _ => state.addError(
                  ref.id.loc,
                  s"${ref.identify} was expected to be ${article(kind.kind)} type but is ${article(defn.kind)} instead"
                )
            }
        }(defaultMultiMatchValidationFunction)
      }
    }

    def checkOption[A <: RiddlValue](
      opt: Option[A],
      name: String,
      thing: Definition
    )(folder: (ValidationState, A) => ValidationState
    ): ValidationState = {
      opt match {
        case None => addMissing(
            thing.loc,
            s"$name in ${thing.identify} should not be empty"
          )
        case Some(x) =>
          val s1 = checkNonEmptyValue(x, "Condition", thing, MissingWarning)
          folder(s1, x)
      }
    }
    def checkSequence[A](
      elements: Seq[A]
    )(fold: (ValidationState, A) => ValidationState
    ): ValidationState = elements.foldLeft(this) { case (next, element) =>
      fold(next, element)
    }

    def checkNonEmptyValue(
      value: RiddlValue,
      name: String,
      thing: Definition,
      kind: KindOfMessage = Error,
      required: Boolean = false
    ): ValidationState = {
      check(
        value.nonEmpty,
        message =
          s"$name in ${thing.identify} ${if (required) "must" else "should"} not be empty",
        kind,
        thing.loc
      )
    }

    def checkNonEmpty(
      list: Seq[?],
      name: String,
      thing: Definition,
      kind: KindOfMessage = Error,
      required: Boolean = false
    ): ValidationState = {
      check(
        list.nonEmpty,
        s"$name in ${thing.identify} ${if (required) "must" else "should"} not be empty",
        kind,
        thing.loc
      )
    }

    def checkOptions[T <: OptionValue](
      options: Seq[T],
      loc: Location
    ): ValidationState = {
      check(
        options.sizeIs == options.distinct.size,
        "Options should not be repeated",
        Error,
        loc
      )
    }

    def checkDefinition(
      parent: Definition,
      definition: Definition
    ): ValidationState = {
      var result = this.check(
        definition.id.nonEmpty | definition.isImplicit,
        "Definitions may not have empty names",
        Error,
        definition.loc
      )
      result = result.checkIdentifierLength(definition)
      val path = symbolTable.pathOf(definition)
      if (!definition.id.isEmpty) {
        val matches = result.lookup[Definition](path)
        if (matches.isEmpty) {
          result = result.addSevere(
            definition.id.loc,
            s"'${definition.id.value}' evaded inclusion in symbol table!"
          )
        } else if (matches.sizeIs >= 2) {
          val parentGroups = matches.groupBy(result.symbolTable.parentOf(_))
          parentGroups.get(Option(parent)) match {
            case Some(head :: tail) if tail.nonEmpty =>
              result = result.addWarning(
                head.id.loc,
                s"${definition.identify} has same name as other definitions in ${parent.identifyWithLoc}:  " +
                  tail.map(x => x.identifyWithLoc).mkString(",  ")
              )
            case Some(head :: tail) if tail.isEmpty =>
              result = result.addStyle(
                head.id.loc,
                s"${definition.identify} has same name as other definitions: " +
                  matches.filterNot(_ == definition).map(x => x.identifyWithLoc)
                    .mkString(",  ")
              )
            case _ =>
            // ignore
          }
        }
      }
      result
    }

    def checkDescription[TD <: DescribedValue](
      id: String,
      value: TD
    ): ValidationState = {
      val description: Option[Description] = value.description
      val shouldCheck: Boolean = {
        value.isInstanceOf[Type] |
          (value.isInstanceOf[Definition] && value.nonEmpty)
      }
      if (description.isEmpty && shouldCheck) {
        this.check(
          predicate = false,
          s"$id should have a description",
          MissingWarning,
          value.loc
        )
      } else if (description.nonEmpty) {
        val desc = description.get
        this.check(
          desc.nonEmpty,
          s"For $id, description at ${desc.loc} is declared but empty",
          MissingWarning,
          desc.loc
        )
      } else this
    }

    def checkDescription[TD <: Definition](
      definition: TD
    ): ValidationState = { checkDescription(definition.identify, definition) }

    def checkContainer(
      maybeParent: Option[Definition],
      container: Definition
    ): ValidationState = {
      val parent = maybeParent.getOrElse(RootContainer.empty)
      checkDefinition(parent, container).check(
        container.nonEmpty || container.isInstanceOf[Field],
        s"${container.identify} in ${parent.identify} should have content",
        MissingWarning,
        container.loc
      )
    }
    def checkAction(
      action: Action,
      defn: Definition
    ): ValidationState = {
      action match {
        case _: ErrorAction => this
        case SetAction(_, path, value, _) => this
            .checkPathRef[Field](path, defn)()().checkExpression(value, defn)
            .checkAssignmentCompatability(path, value)
        case AppendAction(_, value, path, _) => this
            .checkExpression(value, defn).checkPathRef[Field](path, defn)()()
        case ReturnAction(_, expr, _) => this.checkExpression(expr, defn)
        case YieldAction(_, msg, _)   => this.checkMessageConstructor(msg, defn)
        case PublishAction(_, msg, pipeRef, _) => this
            .checkMessageConstructor(msg, defn).checkRef[Pipe](pipeRef, defn)
        case FunctionCallAction(_, funcId, args, _) => this
            .checkPathRef[Function](funcId, defn)()().checkArgList(args, defn)
        case BecomeAction(_, entity, handler, _) => this
            .checkRef[Entity](entity, defn).checkRef[Handler](handler, defn)
        case MorphAction(_, entity, entityState, _) => this
            .checkRef[Entity](entity, defn).checkRef[State](entityState, defn)
        case TellAction(_, msg, entity, _) => this
            .checkRef[Entity](entity, defn).checkMessageConstructor(msg, defn)
        case AskAction(_, entity, msg, _) => this.checkRef[Entity](entity, defn)
            .checkMessageConstructor(msg, defn)
        case ReplyAction(_, msg, _) => checkMessageConstructor(msg, defn)
        case CompoundAction(loc, actions, _) => check(
            actions.nonEmpty,
            "Compound action is empty",
            MissingWarning,
            loc
          ).checkSequence(actions) { (s, action) =>
            s.checkAction(action, defn)
          }
        case ArbitraryAction(loc, what, _) => this.check(
            what.nonEmpty,
            "arbitrary action is empty so specifies nothing",
            MissingWarning,
            loc
          )
      }
    }

    def checkActions(
      actions: Seq[Action],
      defn: Definition
    ): ValidationState = {
      checkSequence(actions)((s, action) => s.checkAction(action, defn))
    }

    def checkExample(
      example: Example
    ): ValidationState = {
      val Example(_, _, givens, whens, thens, buts, _, _) = example
      checkSequence(givens) { (state, givenClause) =>
        state.checkSequence(givenClause.scenario) { (state, ls) =>
          state
            .checkNonEmptyValue(ls, "Given Scenario", example, MissingWarning)
        }.checkNonEmpty(givenClause.scenario, "Givens", example, MissingWarning)
      }.checkSequence(whens) { (st, when) =>
        st.checkExpression(when.condition, example)
      }.checkIf(example.id.nonEmpty) { st =>
        st.checkNonEmpty(thens, "Thens", example, required = true)
      }.checkActions(thens.map(_.action), example)
        .checkActions(buts.map(_.action), example).checkDescription(example)
    }

    def checkExamples(
      examples: Seq[Example]
    ): ValidationState = {
      examples.foldLeft(this) { (next, example) => next.checkExample(example) }
    }

    def checkFunctionCall(
      loc: Location,
      pathId: PathIdentifier,
      args: ArgList,
      defn: Definition
    ): ValidationState = {
      checkArgList(args, defn).checkPathRef[Function](pathId, defn) {
        (state, foundClass, id, defClass, defn) =>
          defaultSingleMatchValidationFunction(
            state,
            foundClass,
            id,
            defClass,
            defn
          )
          defn match {
            case f: Function if f.input.nonEmpty =>
              val fid = f.id
              val fields = f.input.get.fields
              val paramNames = fields.map(_.id.value)
              val argNames = args.args.keys.map(_.value).toSeq
              val s1 = state.check(
                argNames.size == paramNames.size,
                s"Wrong number of arguments for ${fid.format}. Expected ${paramNames
                  .size}, but got ${argNames.size}",
                Error,
                loc
              )
              val missing = paramNames.filterNot(argNames.contains(_))
              val unexpected = argNames.filterNot(paramNames.contains(_))
              val s2 = s1.check(
                missing.isEmpty,
                s"Missing arguments: ${missing.mkString(", ")}",
                Error,
                loc
              )
              s2.check(
                unexpected.isEmpty,
                s"Arguments do not correspond to parameters; ${unexpected.mkString(",")}",
                Error,
                loc
              )
            case _ => state
          }
      }()
    }

    def checkExpressions(
      expressions: Seq[Expression],
      defn: Definition
    ): ValidationState = {
      expressions.foldLeft(this) { (st, expr) =>
        st.checkExpression(expr, defn)
      }
    }

    def checkExpression(
      expression: Expression,
      defn: Definition
    ): ValidationState = expression match {
      case ValueExpression(_, path) =>
        // FIXME: Can we validate based on type? What if a Type is returned?
        checkPathRef[Field](path, defn)(nullSingleMatchingValidationFunction)()
      case GroupExpression(_, expressions) => checkSequence(expressions) {
          (st, expr) => st.checkExpression(expr, defn)
        }
      case FunctionCallExpression(loc, pathId, arguments) =>
        checkFunctionCall(loc, pathId, arguments, defn)
      case ArithmeticOperator(loc, op, operands) => check(
          op.nonEmpty,
          "Operator is empty in abstract binary operator",
          Error,
          loc
        ).checkExpressions(operands, defn)
      case Comparison(_, _, arg1, arg2) => checkExpression(arg1, defn)
          .checkExpression(arg2, defn)
      case AggregateConstructionExpression(_, pid, args) =>
        checkPathRef[Type](pid, defn)()().checkArgList(args, defn)
      case EntityIdExpression(_, entityRef) =>
        checkPathRef[Entity](entityRef, defn)()()
      case Ternary(_, condition, expr1, expr2) =>
        checkExpression(condition, defn).checkExpression(expr1, defn)
          .checkExpression(expr2, defn)
      case NotCondition(_, cond1) => checkExpression(cond1, defn)
      case condition: MultiCondition =>
        checkExpressions(condition.conditions, defn)
      case _ => this // not of interest
    }

    @tailrec private def getPathIdType(
      pid: PathIdentifier,
      parents: Seq[Definition] = parents
    ): Option[TypeExpression] = {
      if (pid.value.isEmpty) { None }
      else {
        val newParents: Seq[Definition] = resolvePath(pid, parents)()()
        val candidate: Option[TypeExpression] = newParents.headOption match {
          case None              => None
          case Some(f: Function) => f.output
          case Some(t: Type)     => Some(t.typ)
          case Some(f: Field)    => Some(f.typeEx)
          case Some(s: State)    => Some(s.aggregation)
          case Some(Pipe(_, _, tt, _, _)) =>
            val te = tt.map(x => AliasedTypeExpression(x.loc, x.id))
            Some(te.getOrElse(Abstract(pid.loc)))
          case Some(Inlet(_, _, typ, _, _, _)) =>
            Some(AliasedTypeExpression(typ.loc, typ.id))
          case Some(Outlet(_, _, typ, _, _, _)) =>
            Some(AliasedTypeExpression(typ.loc, typ.id))
          case Some(_) => Option.empty[TypeExpression]
        }
        candidate match {
          case Some(AliasedTypeExpression(_, pid)) =>
            getPathIdType(pid, newParents)
          case Some(other: TypeExpression) => Some(other)
          case None                        => None
        }
      }
    }

    def isAssignmentCompatible(
      typeEx1: Option[TypeExpression],
      typeEx2: Option[TypeExpression]
    ): Boolean = {
      typeEx1 match {
        case None => false
        case Some(ty1) => typeEx2 match {
            case None      => false
            case Some(ty2) => ty1.isAssignmentCompatible(ty2)
          }
      }
    }

    def getExpressionType(expr: Expression): Option[TypeExpression] = {
      expr match {
        case EntityIdExpression(loc, pid)       => Some(UniqueId(loc, pid))
        case ValueExpression(_, path)           => getPathIdType(path)
        case FunctionCallExpression(_, name, _) => getPathIdType(name)
        case GroupExpression(loc, expressions)  =>
          // the type of a group is the last expression but it could be empty
          expressions.lastOption match {
            case None       => Some(Abstract(loc))
            case Some(expr) => getExpressionType(expr)
          }
        case AggregateConstructionExpression(_, pid, _) => getPathIdType(pid)
        case Ternary(loc, _, expr1, expr2) =>
          val expr1Ty = getExpressionType(expr1)
          val expr2Ty = getExpressionType(expr2)
          if (isAssignmentCompatible(expr1Ty, expr2Ty)) { expr1Ty }
          else {
            addError(
              loc,
              s"""Ternary expressions must be assignment compatible but:
                 |  ${expr1.format} and
                 |  ${expr2.format}
                 |are incompatible
                 |""".stripMargin
            )
            None
          }
        case e: Expression => Some(e.expressionType)
      }
    }

    def checkAssignmentCompatability(
      path: PathIdentifier,
      expr: Expression
    ): ValidationState = {
      val pidType = getPathIdType(path, parents)
      val exprType = getExpressionType(expr)
      if (!isAssignmentCompatible(pidType, exprType)) {
        addError(
          path.loc,
          s"""Setting a value requires assignment compatibility, but field:
             |  ${path.format} (${pidType.map(_.format).getOrElse("<not found>")})
             |is not assignment compatible with expression:
             |  ${expr.format} (${exprType.map(_.format)
            .getOrElse("<not found>")})
             |""".stripMargin
        )
      } else { this }
    }

    def checkArgList(
      arguments: ArgList,
      defn: Definition
    ): ValidationState = {
      arguments.args.values.foldLeft(this) { (st, arg) =>
        st.checkExpression(arg, defn)
      }
    }

    def checkMessageConstructor(
      messageConstructor: MessageConstructor,
      defn: Definition
    ): ValidationState = {
      val id = messageConstructor.msg.id
      val kind = messageConstructor.msg.messageKind.kind
      checkPathRef[Type](id, defn, Some(kind)) { (state, _, id, _, defn) =>
        defn match {
          case Type(_, _, typ, _, _) => typ match {
              case mt: MessageType =>
                val names = messageConstructor.args.args.keys.map(_.value).toSeq
                val unset = mt.fields.filterNot { fName =>
                  names.contains(fName.id.value)
                }
                if (unset.nonEmpty) {
                  unset.filterNot(_.isImplicit).foldLeft(state) {
                    (next, field) =>
                      next.addError(
                        messageConstructor.loc,
                        s"${field.identify} was not set in message constructor"
                      )
                  }
                } else { state }
              case te: TypeExpression => state.addError(
                  id.loc,
                  s"'${id.format}' should reference a message type but is a ${AST.kind(te)} type instead."
                )
            }
          case _ => addError(
              id.loc,
              s"'${id.format}' was expected to be a message type but is ${article(defn.kind)} instead"
            )
        }
      }(defaultMultiMatchValidationFunction)
    }

    def checkProcessorKind(proc: Processor): ValidationState = {
      val ins = proc.inlets.size
      val outs = proc.outlets.size
      proc.shape match {
        case AST.Source(loc) =>
          if (ins != 0 || outs != 1) {
            this.addError(
              loc,
              s"${proc.identify} should have 1 Outlet and no Inlets but has $outs and $ins "
            )
          } else { this }
        case AST.Flow(loc) =>
          if (ins != 1 || outs != 1) {
            this.addError(
              loc,
              s"${proc.identify} should have 1 Outlet and 1 Inlet but has $outs and $ins"
            )
          } else { this }
        case AST.Sink(loc) =>
          if (ins != 1 || outs != 0) {
            this.addError(
              loc,
              s"${proc.identify} should have no Outlets and 1 Inlet but has $outs and $ins"
            )
          } else { this }
        case AST.Merge(loc) =>
          if (ins < 2 || outs != 1) {
            this.addError(
              loc,
              s"${proc.identify} should have 1 Outlet and >1 Inlets but has $outs and $ins"
            )
          } else { this }
        case AST.Split(loc) =>
          if (ins != 1 || outs < 2) {
            this.addError(
              loc,
              s"${proc.identify} should have >1 Outlets and 1 Inlet but has $outs and $ins"
            )
          } else { this }
        case AST.Multi(loc) =>
          if (ins < 2 || outs < 2) {
            this.addError(
              loc,
              s"${proc.identify} should have >1 Outlets and >1 Inlets but has $outs and $ins"
            )
          } else { this }
        case AST.Void(loc) =>
          if (ins > 0 || outs > 0) {
            this.addError(
              loc,
              s"${proc.identify} should have no Outlets or Inlets but has $outs and $ins"
            )
          } else { this }
      }
    }
  }
}
