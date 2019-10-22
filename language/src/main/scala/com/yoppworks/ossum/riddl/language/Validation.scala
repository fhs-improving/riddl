package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._

import scala.reflect.ClassTag
import scala.reflect.classTag

/** Validates an AST */
object Validation {

  sealed trait ValidationMessageKind {
    def isError: Boolean = false
    def isWarning: Boolean = false
  }
  case object MissingWarning extends ValidationMessageKind {
    override def isWarning = true
  }
  case object StyleWarning extends ValidationMessageKind {
    override def isWarning = true
  }
  case object Warning extends ValidationMessageKind {
    override def isWarning = true
  }
  case object Error extends ValidationMessageKind {
    override def isError = true
  }
  case object SevereError extends ValidationMessageKind {
    override def isError = true
  }

  case class ValidationMessage(
    loc: Location,
    message: String,
    kind: ValidationMessageKind = Error
  ) {

    def format(source: String): String = {
      source + s" $loc: $message"
    }
  }

  type ValidationMessages = List[ValidationMessage]

  val NoValidationMessages: List[ValidationMessage] =
    List.empty[ValidationMessage]

  object ValidationMessages {

    def apply(): ValidationMessages = {
      List[ValidationMessage]()
    }

    def apply(msg: ValidationMessage): ValidationMessages = {
      apply() :+ msg
    }

    def apply(msgs: ValidationMessage*): ValidationMessages = {
      apply() ++ msgs
    }
  }

  sealed trait ValidationOptions

  /** Warn when recommended features are left unused */
  case object ReportMissingWarnings extends ValidationOptions

  /** Warn when recommended stylistic violations occur */
  case object ReportStyleWarnings extends ValidationOptions

  val defaultOptions: Seq[ValidationOptions] = Seq(
    ReportMissingWarnings,
    ReportStyleWarnings
  )

  case class ValidationState(
    symbolTable: SymbolTable,
    options: Seq[ValidationOptions] = defaultOptions,
    msgs: ValidationMessages = NoValidationMessages
  ) {

    def parentOf(
      definition: Definition
    ): ContainingDefinition = {
      symbolTable.parentOf(definition).getOrElse(RootContainer.empty)
    }

    def isReportMissingWarnings: Boolean =
      options.contains(ReportMissingWarnings)

    def isReportStyleWarnings: Boolean =
      options.contains(ReportStyleWarnings)

    def lookup[T <: Definition: ClassTag](
      id: Identifier,
      within: ContainingDefinition
    ): List[T] = {
      symbolTable.lookup[T](id, within)
    }

    def add(msg: ValidationMessage): ValidationState = {
      msg.kind match {
        case StyleWarning =>
          if (isReportStyleWarnings)
            this.copy(msgs = msgs :+ msg)
          else
            this
        case MissingWarning =>
          if (isReportMissingWarnings)
            this.copy(msgs = msgs :+ msg)
          else this

        case _ =>
          this.copy(msgs = msgs :+ msg)
      }
    }

    def check(
      predicate: Boolean = true,
      message: => String,
      kind: ValidationMessageKind,
      loc: Location
    ): ValidationState = {
      if (!predicate) {
        add(ValidationMessage(loc, message, kind))
      } else {
        this
      }
    }

    def checkTypeExpression(
      typ: TypeExpression,
      definition: ContainingDefinition
    ): ValidationState = {
      typ match {
        case Optional(_, id) =>
          checkRef[TypeDefinition](id, definition)
        case OneOrMore(_, id) =>
          checkRef[TypeDefinition](id, definition)
        case ZeroOrMore(_, id) =>
          checkRef[TypeDefinition](id, definition)
        case AST.TypeRef(_, id) =>
          checkRef[TypeDefinition](id, definition)
      }
    }

    def checkRef[T <: Definition: ClassTag](
      reference: Reference,
      within: ContainingDefinition
    ): ValidationState = {
      checkRef[T](reference.id, within)
    }

    def checkRef[T <: Definition: ClassTag](
      id: Identifier,
      within: ContainingDefinition
    ): ValidationState = {
      val tc = classTag[T].runtimeClass
      symbolTable.lookup[T](id, within) match {
        case Nil =>
          add(
            ValidationMessage(
              id.loc,
              s"Identifier ${id.value} is not defined but should be a ${tc.getSimpleName}",
              Error
            )
          )
        case d :: Nil =>
          check(
            tc.isAssignableFrom(d.getClass),
            s"Identifier ${id.value} was expected to be ${tc.getSimpleName}" +
              s" but is ${d.getClass.getSimpleName} instead",
            Error,
            id.loc
          )
        case _ :: tail =>
          add(
            ValidationMessage(
              id.loc,
              s"""Identifier ${id.value} is not uniquely defined. Other
                 |definitions are:
                 |${tail.map(_.loc.toString).mkString("  \n")}",
                 |""".stripMargin,
              Error
            )
          )
      }
    }

    def checkNonEmpty(
      list: Seq[_],
      name: String,
      thing: Definition
    ): ValidationState = {
      check(
        list.nonEmpty,
        s"$name in ${thing.identify} should not be empty",
        Error,
        thing.loc
      )
    }

    def checkLiteralString(
      litStr: LiteralString,
      name: String,
      thing: Definition
    ): ValidationState = {
      check(
        litStr.s.nonEmpty,
        s"$name in ${thing.identify} should not be empty",
        MissingWarning,
        thing.loc
      )
    }

    def checkOptions[T](options: Seq[T], loc: Location): ValidationState = {
      check(
        options.size == options.distinct.size,
        "Options should not be repeated",
        Error,
        loc
      )
    }
  }

  def validate[C <: ContainingDefinition](
    root: C,
    options: Seq[ValidationOptions] = defaultOptions
  ): Seq[ValidationMessage] = {
    val symTab = SymbolTable(root)
    val state = ValidationState(symTab, options)
    val validatedState =
      Folding.foldLeft[ValidationState](root, root, state)(validationDispatch)
    validatedState.msgs
  }

  def validationDispatch[C <: ContainingDefinition](
    container: ContainingDefinition,
    definition: Definition,
    state: ValidationState
  ): ValidationState = {
    val result = checkDefinition(container, definition, state)
    definition match {
      case root: RootContainer =>
        checkRoot(result, container, root)
      case domain: DomainDef =>
        checkDomain(result, container, domain)
      case context: ContextDef =>
        checkContext(result, container, context)
      case entity: EntityDef =>
        checkEntity(result, container, entity)
      case command: CommandDef =>
        checkCommand(result, container, command)
      case event: EventDef =>
        checkEvent(result, container, event)
      case query: QueryDef =>
        checkQuery(result, container, query)
      case rslt: ResultDef =>
        checkResult(result, container, rslt)
      case feature: FeatureDef =>
        checkFeature(result, container, feature)
      case adaptor: AdaptorDef =>
        checkAdaptor(result, container, adaptor)
      case channel: ChannelDef =>
        checkChannel(result, container, channel)
      case interaction: InteractionDef =>
        checkInteraction(result, container, interaction)
      case typ: TypeDef =>
        checkType(result, container, typ)
      case action: ActionDef =>
        checkAction(result, container, action)
      case example: ExampleDef =>
        checkExample(result, container, example)
      case function: FunctionDef =>
        checkFunction(result, container, function)
      case invariant: InvariantDef =>
        checkInvariant(result, container, invariant)
      case role: RoleDef =>
        checkRole(result, container, role)
      case predef: PredefinedType =>
        checkPredefinedType(result, container, predef)
      case rule: TranslationRule =>
        checkTranslationRule(result, container, rule)
    }
  }

  def checkDefinition(
    container: ContainingDefinition,
    definition: Definition,
    state: ValidationState
  ): ValidationState = {
    var result = state.check(
      definition.id.value.nonEmpty,
      "Definitions may not have empty names",
      Error,
      definition.loc
    )
    result = result.check(
      definition.id.value.length > 2,
      s"${definition.identify} has a name that is too short.",
      StyleWarning,
      definition.loc
    )
    val matches =
      result.lookup[Definition](definition.id, container)
    if (matches.isEmpty) {
      result = result.add(
        ValidationMessage(
          definition.id.loc,
          s"'${definition.id.value}' evaded inclusion in symbol table!",
          SevereError
        )
      )
    } else if (matches.size >= 2) {
      matches
        .groupBy(result.symbolTable.parentOf(_))
        .get(Some(container)) match {
        case Some(head :: tail) if tail.nonEmpty =>
          result = result.add(
            ValidationMessage(
              head.id.loc,
              s"${head.identify} is defined multiple times; other " +
                s"definitions are:\n  " +
                matches.map(x => x.identify + " " + x.loc).mkString("\n  "),
              Error
            )
          )
        case _ =>
      }
    }
    checkAddendum(definition, result, definition.addendum)
  }

  def checkAddendum(
    definition: Definition,
    state: ValidationState,
    add: Option[Addendum]
  ): ValidationState = {
    var result = state
    if (add.isEmpty) {
      result = result.check(
        predicate = false,
        s"Definition '${definition.id.value}' " +
          "should have explanations or references",
        MissingWarning,
        definition.loc
      )
    } else {
      val explanation = add.get.explanation
      if (explanation.nonEmpty)
        result = checkExplanation(result, explanation.get)
      val seeAlso = add.get.seeAlso
      if (seeAlso.nonEmpty)
        result = checkSeeAlso(result, seeAlso.get)
    }
    result
  }

  protected def checkExplanation(
    state: ValidationState,
    exp: Explanation
  ): ValidationState = {
    state.check(
      exp.markdown.nonEmpty,
      "Explanations should not be empty",
      MissingWarning,
      exp.loc
    )
  }

  protected def checkSeeAlso(
    state: ValidationState,
    seeAlso: SeeAlso
  ): ValidationState = {
    state.check(
      seeAlso.citations.nonEmpty,
      "SeeAlso references should not be empty",
      MissingWarning,
      seeAlso.loc
    )
  }

  def checkRoot(
    state: ValidationState,
    container: ContainingDefinition,
    root: RootContainer
  ): ValidationState = {
    state
  }

  def checkDomain(
    state: ValidationState,
    container: ContainingDefinition,
    domain: DomainDef
  ): ValidationState = {
    var result = state
    domain.subdomain.foreach { id =>
      result = result.checkRef[DomainDef](id, state.parentOf(domain))
    }
    result
  }

  def checkContext(
    state: Validation.ValidationState,
    container: ContainingDefinition,
    context: AST.ContextDef
  ): ValidationState = {
    var result = state
    result = result.checkOptions[ContextOption](context.options, context.loc)
    if (context.entities.isEmpty) {
      result.add(
        ValidationMessage(
          context.loc,
          "Contexts that define no entities are not valid",
          Error
        )
      )
    } else {
      result
    }
  }

  def checkEntity(
    state: Validation.ValidationState,
    container: ContainingDefinition,
    entity: AST.EntityDef
  ): ValidationState = {
    val result1 = state
      .checkTypeExpression(entity.typ, entity)
      .checkOptions[EntityOption](entity.options, entity.loc)
    val result2: ValidationState =
      entity.produces.foldLeft(result1) { (s, chan) =>
        val persistentClass = EntityPersistent((0, 0)).getClass
        val lookupResult = s
          .checkRef[ChannelDef](chan, entity)
          .symbolTable
          .lookup[ChannelDef](chan, entity)
        lookupResult match {
          case chan :: Nil =>
            s.check(
              !(chan.events.isEmpty &&
                entity.options.exists(_.getClass == persistentClass)),
              s"Persistent ${entity.identify} requires a channel that produces " +
                s"events but ${chan.identify} does not define any.",
              Error,
              chan.loc
            )
          case _ =>
            s
        }
      }
    var result = entity.consumes.foldLeft(result2) { (s, chan) =>
      s.checkRef[ChannelDef](chan, entity)
    }

    // TODO: invariant?

    if (entity.consumes.isEmpty) {
      result = result.add(
        ValidationMessage(entity.loc, "An entity must consume a channel")
      )
    }
    if (entity.produces.isEmpty &&
        entity.options.exists(_.getClass == classOf[EntityPersistent])) {
      result = result.add(
        ValidationMessage(
          entity.loc,
          "An entity that produces no events on a channel cannot be persistent"
        )
      )
    }
    result
  }

  def checkChannel(
    state: ValidationState,
    container: ContainingDefinition,
    channel: ChannelDef
  ): ValidationState = {
    var result = state.check(
      channel.results.size + channel.queries.size + channel.commands.size +
        channel.events.size > 0,
      s"${channel.identify} does not define any messages",
      MissingWarning,
      channel.loc
    )

    result = channel.commands.foldLeft(result) {
      case (s, ref) => s.checkRef[CommandDef](ref, container)
    }

    result = channel.events.foldLeft(result) {
      case (s, ref) => s.checkRef[EventDef](ref, container)
    }

    result = channel.queries.foldLeft(result) {
      case (s, ref) => s.checkRef[QueryDef](ref, container)
    }
    result = channel.results.foldLeft(result) {
      case (s, ref) => s.checkRef[ResultDef](ref, container)
    }
    result
  }

  def checkCommand(
    state: ValidationState,
    container: ContainingDefinition,
    command: CommandDef
  ): ValidationState = {
    var result = state.checkTypeExpression(command.typ, container)
    if (command.events.isEmpty) {
      result = result.add(
        ValidationMessage(
          command.loc,
          "Commands must always yield at least one event"
        )
      )
    } else {
      command.events.foreach { eventRef =>
        result = result.checkRef[EventDef](eventRef, container)
      }
    }
    result
  }

  def checkEvent(
    state: ValidationState,
    container: ContainingDefinition,
    event: EventDef
  ): ValidationState = {
    state.checkTypeExpression(event.typ, container)
  }

  def checkQuery(
    state: ValidationState,
    container: ContainingDefinition,
    query: QueryDef
  ): ValidationState = {
    val result = state.checkTypeExpression(query.typ, container)
    result.checkRef[ResultDef](query.result.id, container)
  }

  def checkResult(
    state: ValidationState,
    container: ContainingDefinition,
    result: ResultDef
  ): ValidationState = {
    state.checkTypeExpression(result.typ, container)
  }

  def checkFeature(
    state: Validation.ValidationState,
    container: ContainingDefinition,
    feature: FeatureDef
  ): ValidationState = {
    var result =
      state.checkNonEmpty(feature.description, "Description", feature)
    result = feature.background.foldLeft(result) {
      case (s, bg) => s.checkNonEmpty(bg.givens, "Background", feature)
    }

    result = feature.examples.foldLeft(result) {
      case (s, example) =>
        s.checkLiteralString(example.description, "Description", example)
          .checkNonEmpty(example.givens, "Givens", example)
          .checkNonEmpty(example.whens, "Whens", example)
          .checkNonEmpty(example.thens, "Thens", example)
    }
    result
  }

  def checkAdaptor(
    state: Validation.ValidationState,
    container: ContainingDefinition,
    adaptor: AdaptorDef
  ): ValidationState = {
    val result = adaptor.targetDomain.foldLeft(state) {
      case (s, domain) => s.checkRef[DomainDef](domain, adaptor)
    }
    result.checkRef[ContextDef](adaptor.targetContext, adaptor)
  }

  def checkInteraction(
    state: Validation.ValidationState,
    container: ContainingDefinition,
    interaction: InteractionDef
  ): ValidationState = {

    var result =
      state.checkNonEmpty(interaction.actions, "Actions", interaction)

    result = interaction.roles.foldLeft(result) {
      case (s, role) =>
        s.checkOptions[RoleOption](role.options, role.loc)
          .checkNonEmpty(role.responsibilities, "Responsibilities", role)
          .checkNonEmpty(role.capacities, "Capacities", role)
    }

    interaction.actions.foldLeft(result) {
      case (s, action) =>
        action match {
          case ma: MessageActionDef =>
            val newState = s
              .checkRef[EntityDef](ma.receiver, interaction)
              .checkRef[EntityDef](ma.sender, interaction)
              .checkRef[MessageDefinition](ma.message, interaction)
            ma.reactions.foldLeft(newState) {
              case (s, reaction) => s.checkRef(reaction.entity, interaction)
            }

          case da: DirectiveActionDef =>
            val newState = s
              .checkRef[EntityDef](da.entity, interaction)
              .checkRef[MessageDefinition](da.message, interaction)
              .checkRef[RoleDef](da.role, interaction)
            da.reactions.foldLeft(newState) {
              case (s, reaction) => s.checkRef(reaction.entity, interaction)
            }
        }
    }
  }

  def checkType(
    state: ValidationState,
    container: ContainingDefinition,
    typeDef: TypeDef
  ): ValidationState = {
    val result = state.check(
      typeDef.id.value.head.isUpper,
      s"Type name ${typeDef.id.value} must start with a capital letter",
      StyleWarning,
      typeDef.loc
    )
    typeDef.typ match {
      case pd: PredefinedType =>
        result.checkRef[TypeDefinition](pd.id, container)
      case Optional(_: Location, typeName: Identifier) =>
        result.checkRef[TypeDefinition](typeName, container)
      case ZeroOrMore(_: Location, typeName: Identifier) =>
        result.checkRef[TypeDefinition](typeName, container)
      case OneOrMore(_: Location, typeName: Identifier) =>
        result.checkRef[TypeDefinition](typeName, container)
      case TypeRef(_, typeName) =>
        result.checkRef[TypeDefinition](typeName, container)
      case Aggregation(_, of) =>
        of.foldLeft(result) {
          case (s, (id: Identifier, typEx: TypeExpression)) =>
            s.checkTypeExpression(typEx, container)
              .check(
                id.value.head.isLower,
                "Field names should start with a lower case letter",
                StyleWarning,
                typEx.loc
              )
        }
      case Alternation(_, of) =>
        of.foldLeft(result) {
          case (s, typEx: TypeExpression) =>
            s.checkRef[TypeDefinition](typEx.id, container)
        }
      case Enumeration(_, of) =>
        of.foldLeft(result) {
          case (s, id) =>
            s.check(
              id.value.head.isUpper,
              s"Enumerator '${id.value}' must start with lower case",
              StyleWarning,
              id.loc
            )
        }
      case Mapping(_, from, to) =>
        result
          .checkTypeExpression(from, container)
          .checkTypeExpression(to, container)
      case tyEx @ RangeType(_, min, max) =>
        result
          .check(
            min.n >= BigInt.long2bigInt(Long.MinValue),
            "Minimum value might be too small to store in a Long",
            Warning,
            tyEx.loc
          )
          .check(
            max.n <= BigInt.long2bigInt(Long.MaxValue),
            "Maximum value might be too small to store in a Long",
            Warning,
            tyEx.loc
          )
      case ReferenceType(_, entity) =>
        result.checkRef[EntityDef](entity, container)
    }
  }

  def checkPredefinedType(
    result: ValidationState,
    container: ContainingDefinition,
    predef: PredefinedType
  ): ValidationState = {
    result
  }

  def checkAction(
    result: ValidationState,
    container: ContainingDefinition,
    action: ActionDef
  ): ValidationState = {
    result
  }

  def checkExample(
    result: ValidationState,
    container: ContainingDefinition,
    example: ExampleDef
  ): ValidationState = {
    result
  }

  def checkFunction(
    result: ValidationState,
    container: ContainingDefinition,
    function: FunctionDef
  ): ValidationState = {
    result
  }

  def checkInvariant(
    result: ValidationState,
    container: ContainingDefinition,
    invariant: InvariantDef
  ): ValidationState = {
    result
  }

  def checkRole(
    result: ValidationState,
    container: ContainingDefinition,
    role: RoleDef
  ): ValidationState = {
    result
  }

  def checkTranslationRule(
    result: ValidationState,
    container: ContainingDefinition,
    rule: TranslationRule
  ): ValidationState = {
    result
  }

}