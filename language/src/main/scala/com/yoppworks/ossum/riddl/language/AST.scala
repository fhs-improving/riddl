package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.Terminals.Keywords

import scala.collection.immutable.ListMap
import scala.reflect.ClassTag

// scalastyle:off number.of.methods

/** Abstract Syntax Tree This object defines the model for processing RIDDL and producing a raw AST
 * from it. This raw AST has no referential integrity, it just results from applying the parsing
 * rules to the input. The RawAST models produced from parsing are syntactically correct but have
 * no semantic validation. The Transformation passes convert RawAST model to AST model which is
 * referentially and semantically consistent (or the user gets an error).
 */
object AST {

  /** The root trait of all things RIDDL AST. Every node in the tree is a RiddlNode. */
  sealed trait RiddlNode {
    def format: String = ""

    def isEmpty: Boolean = false

    @deprecatedOverriding("nonEmpty is defined as !isEmpty; override isEmpty instead")
    def nonEmpty: Boolean = !isEmpty
  }

  /** The root trait of all parsable values. If a parser returns something, its a RiddlValue */
  sealed trait RiddlValue extends RiddlNode {
    def loc: Location
  }

  /**
   * A RiddlValue that is a parsed identifier, typically the name of a definition.
   *
   * @param loc   The location in the input where the identifier starts
   * @param value The parsed value of the identifer
   */
  case class Identifier(loc: Location, value: String) extends RiddlValue {
    override def format: String = value

    override def isEmpty: Boolean = value.isEmpty
  }

  /**
   * Represents a literal string parsed between quote characters in the input
   *
   * @param loc The location in the input of the opening quote character
   * @param s   The parsed value of the string content
   */
  case class LiteralString(loc: Location, s: String) extends Condition {
    override def format = s"\"$s\""

    override def isEmpty: Boolean = s.isEmpty
  }

  /**
   * Represents a segmented identifier to a definition in the model. Path Identifiers are parsed
   * from a dot-separated list of identifiers in the input. Path identifiers are used to
   * reference other definitions in the model.
   *
   * @param loc   Location in the input of the first letter of the path identifier
   * @param value The list of strings that make up the path identifer
   */
  case class PathIdentifier(loc: Location, value: Seq[String]) extends RiddlValue {
    override def format: String = {
      value.reverse.mkString(".")
    }

    override def isEmpty: Boolean = value.isEmpty || value.forall(_.isEmpty)
  }

  /**
   * The description of a definition. All definitions have a name and an optional description.
   * This class provides the description part.
   *
   * @param loc   The location in the input of the description
   * @param lines The lines of markdown that provide the description
   */
  case class Description(
    loc: Location = 0 -> 0,
    lines: Seq[LiteralString] = Seq.empty[LiteralString])
    extends RiddlValue {
    override def isEmpty: Boolean = lines.isEmpty || lines.forall(_.s.isEmpty)
  }

  /**
   * A reference to a definition of a specific type.
   *
   * @tparam T The type of definition to which the references refers.
   */
  sealed abstract class Reference[+T <: Definition] extends RiddlValue {
    def id: PathIdentifier

    override def isEmpty: Boolean = id.isEmpty
  }

  /**
   * Base trait of all values that have an optional Description
   */
  sealed trait DescribedValue extends RiddlValue {
    def description: Option[Description]
  }

  /**
   * Base trait of all values that contain definitions
   *
   * @tparam CT The contained type of definition
   */
  sealed trait ContainerValue[+CT <: Definition] extends RiddlValue {
    def contents: Seq[CT]

    override def isEmpty: Boolean = contents.isEmpty
  }

  /**
   * A function to translate between a definition and the keyword that introduces them.
   *
   * @param definition The definition to look up
   * @return A string providing the definition keyword, if any. Enumerators and fields don't have
   *         their own keywords
   */
  def keyword(definition: Definition): String = {
    definition match {
      case _: Adaptation => Keywords.adapt
      case _: Adaptor => Keywords.adaptor
      case _: Context => Keywords.context
      case _: Domain => Keywords.domain
      case _: Entity => Keywords.entity
      case _: Enumerator => ""
      case _: Example => Keywords.example
      case _: Feature => Keywords.feature
      case _: Field => ""
      case _: Function => Keywords.function
      case _: Handler => Keywords.handler
      case _: Inlet => Keywords.inlet
      case _: Interaction => Keywords.interaction
      case _: Invariant => Keywords.invariant
      case _: Joint => Keywords.joint
      case _: MessageAction => Keywords.message
      case _: Outlet => Keywords.outlet
      case _: Pipe => Keywords.pipe
      case _: Plant => Keywords.plant
      case _: Processor => Keywords.processor
      case _: RootContainer => ""
      case _: Saga => Keywords.saga
      case _: SagaAction => Keywords.action
      case _: State => Keywords.state
      case _: Type => Keywords.`type`
      case _ => ""
    }
  }

  /**
   * A function to provide the kind of thing that a DescribedValue is
   *
   * @param definition The DescribedValue for which the kind is returned
   * @return A string for the kind of DescribedValue
   */
  def kind(definition: DescribedValue): String = {
    definition match {
      case _: Adaptation => "Adaptation"
      case _: Adaptor => "Adaptor"
      case _: Context => "Context"
      case _: Domain => "Domain"
      case _: Entity => "Entity"
      case _: Enumerator => "Enumerator"
      case _: Example => "Example"
      case _: Feature => "Feature"
      case _: Field => "Field"
      case _: Function => "Function"
      case _: Handler => "Handler"
      case _: Inlet => "Inlet"
      case _: Interaction => "Interaction"
      case _: Invariant => "Invariant"
      case _: Joint => "Joint"
      case _: MessageAction => "Message"
      case _: Outlet => "Outlet"
      case _: Pipe => "Pipe"
      case _: Plant => "Plant"
      case _: Processor => "Processor"
      case _: RootContainer => ""
      case _: Saga => "Saga"
      case _: SagaAction => "SagaAction"
      case _: State => "State"
      case _: Type => "Type"
      case _: AskAction => "Ask Action"
      case _: BecomeAction => "Become Action"
      case _: MorphAction => "Morph Action"
      case _: SetAction => "Set Action"
      case _: PublishAction => "Publish Action"
      case _: TellAction => "Tell Action"
      case _: ArbitraryAction => "Arbitrary Action"
      case _: OnClause => "On Clause"
      case _ => "Definition"
    }
  }

  /**
   * Base trait for all definitions requiring an identifier for the definition and providing the
   * identify method to yield a string that provides the kind and name
   */
  sealed trait Definition extends DescribedValue {
    def id: Identifier

    def identify: String = s"${AST.kind(this)} '${id.format}'"
  }

  /**
   * Base trait of any definition that is in the content of an adaptor
   */
  sealed trait AdaptorDefinition extends Definition

  /**
   * Base trait of any definition that is in the content of a context
   */
  sealed trait ContextDefinition extends Definition

  /**
   * Base trait of any definition that is in the content of a domain
   */
  sealed trait DomainDefinition extends Definition

  /**
   * Base trait of any definition that is in the content of an entity.
   */
  sealed trait EntityDefinition extends Definition

  /** Base trait of any definition that is also a ContainerValue
   *
   * @tparam CV The kind of definition that is contained by the container
   */
  sealed trait Container[+CV <: Definition] extends Definition with ContainerValue[CV]

  /**
   * The root of the containment hierarchy, corresponding roughly to a level about a file.
   *
   * @param domains The sequence of domains contained by the root
   */
  case class RootContainer(domains: Seq[Domain]) extends Container[Domain] {

    def id: Identifier = Identifier((0, 0), "<file root>")

    def description: Option[Description] = None

    def loc: Location = (0, 0)

    override def contents: Seq[Domain] = domains
  }

  object RootContainer {
    val empty: RootContainer = RootContainer(Seq.empty[Domain])
  }

  /**
   * Base trait for option values for any option of a definition.
   */
  trait OptionValue extends RiddlValue {
    def name: String

    def args: Seq[LiteralString] = Seq.empty[LiteralString]

    override def format: String = name + args.map(_.format).mkString("(", ", ", ")")
  }

  /**
   * Base trait that can be used in any definition that takes options and ensures
   * the options are defined, can be queried, and formatted.
   *
   * @tparam T The sealed base trait of the permitted options for this definition
   */
  trait OptionsDef[T <: OptionValue] extends RiddlValue {
    def options: Seq[T]

    def hasOption[OPT <: T : ClassTag]: Boolean = options
      .exists(_.getClass == implicitly[ClassTag[OPT]].runtimeClass)

    override def format: String = {
      options.size match {
        case 0 => ""
        case 1 => s"option is ${options.head.format}"
        case x: Int if x > 1 => s"options ( ${options.map(_.format).mkString(" ", ", ", " )")}"
      }
    }

    override def isEmpty: Boolean = options.isEmpty
  }

  // ////////////////////////////////////////////////////////// TYPES

  /**
   * Base trait of an expression that defines a type
   */
  sealed trait TypeExpression extends RiddlValue

  /**
   * A utility function for getting the kind of a type expression.
   *
   * @param te The type expression to examine
   * @return A string indicating the kind corresponding to te
   */
  def kind(te: TypeExpression): String = {
    te match {
      case TypeRef(_, id) => s"Reference To ${id.format}"
      case Optional(_, typeExp) => kind(typeExp) + "?"
      case ZeroOrMore(_, typeExp) => kind(typeExp) + "*"
      case OneOrMore(_, typeExp) => kind(typeExp) + "+"
      case _: Enumeration => "Enumeration"
      case _: Alternation => "Alternation"
      case _: Aggregation => "Aggregation"
      case Mapping(_, from, to) => s"Map From ${kind(from)} To ${kind(to)}"
      case RangeType(_, min, max) => s"Range($min,$max)"
      case ReferenceType(_, entity) => s"Reference To Entity ${entity.id.format}"
      case _: Pattern => s"Pattern"
      case UniqueId(_, entityPath) => s"Id(${entityPath.format})"
      case MessageType(_, messageKind, _) => messageKind.kind
      case predefinedType: PredefinedType => predefinedType.kind
      case _ => "<unknown type expression>"
    }
  }

  /**
   * Base trait for a type expression that is also a container value
   */
  sealed trait TypeContainer extends TypeExpression with ContainerValue[Type]

  /**
   * A reference to a type definition
   *
   * @param loc The location in the source where the reference to the type is made
   * @param id  The path identifier of the reference type
   */
  case class TypeRef(loc: Location, id: PathIdentifier) extends Reference[Type] with
    TypeExpression {
    override def format: String = s"type ${id.format}"
  }

  /** Base of an enumeration for the four kinds of message types */
  sealed trait MessageKind {
    def kind: String
  }

  /** An enumerator value for command types */
  final case object CommandKind extends MessageKind {
    def kind: String = "command"
  }

  /** An enumerator value for event types */
  final case object EventKind extends MessageKind {
    def kind: String = "event"
  }

  /** An enumerator value for query types */
  final case object QueryKind extends MessageKind {
    def kind: String = "query"
  }

  /** An enumerator value for result types */
  final case object ResultKind extends MessageKind {
    def kind: String = "result"
  }

  /** Base trait for the four kinds of message references */
  sealed trait MessageRef extends Reference[Type] {
    def messageKind: MessageKind

    override def format: String = s"${messageKind.kind} ${id.format}"
  }

  /** A Reference to a command type
   *
   * @param loc The location of the reference
   * @param id  The path identifier to the event type
   */
  case class CommandRef(loc: Location, id: PathIdentifier) extends MessageRef {
    def messageKind: MessageKind = CommandKind
  }

  /** A Reference to an event type
   *
   * @param loc The location of the reference
   * @param id  The path identifier to the event type
   */
  case class EventRef(loc: Location, id: PathIdentifier) extends MessageRef {
    def messageKind: MessageKind = EventKind
  }

  /** A reference to a query type
   *
   * @param loc The location of the reference
   * @param id  The path identifier to the query type
   */
  case class QueryRef(loc: Location, id: PathIdentifier) extends MessageRef {
    def messageKind: MessageKind = QueryKind
  }

  /** A reference to a result type
   *
   * @param loc The location of the reference
   * @param id  The path identifier to the result type
   */
  case class ResultRef(loc: Location, id: PathIdentifier) extends MessageRef {
    def messageKind: MessageKind = ResultKind
  }

  /** Base trait of the cardinality type expressions */
  sealed trait Cardinality extends TypeExpression

  /**
   * A cardinality type expression that indicates another type expression as being optional; that
   * is with a cardinality of 0 or 1.
   *
   * @param loc     The location of the optional cardinality
   * @param typeExp The type expression that is indicated as optional
   */
  case class Optional(loc: Location, typeExp: TypeExpression) extends Cardinality

  /**
   * A cardinality type expression that indicates another type expression as having zero or more
   * instances.
   *
   * @param loc     The location of the zero-or-more cardinality
   * @param typeExp The type expression that is indicated with a cardinality of zero or more.
   */
  case class ZeroOrMore(loc: Location, typeExp: TypeExpression) extends Cardinality

  /**
   * A cardinality type expression that indicates another type expression as having one or more
   * instances.
   *
   * @param loc     The location of the one-or-more cardinality
   * @param typeExp The type expression that is indicated with a cardinality of one or more.
   */
  case class OneOrMore(loc: Location, typeExp: TypeExpression) extends Cardinality

  /** Represents one variant among (one or) many variants that comprise an [[Enumeration]]
   *
   * @param id
   * the identifier (name) of the Enumerator
   * @param enumVal
   * the optional int value
   * @param description
   * the description of the enumerator. Each Enumerator in an enumeration may define independent
   * descriptions
   */
  case class Enumerator(
    loc: Location,
    id: Identifier,
    enumVal: Option[LiteralInteger] = None,
    description: Option[Description] = None)
    extends Definition

  /**
   * A type expression that defines its range of possible values as being one value from a set of
   * enumerated values.
   *
   * @param loc         The location of the enumeration type expression
   * @param enumerators The set of enumerators from which the value of this enumeration may be
   *                    chosen.
   */
  case class Enumeration(
    loc: Location,
    enumerators: Seq[Enumerator])
    extends TypeExpression with ContainerValue[Enumerator] {
    lazy val contents: Seq[Enumerator] = enumerators
  }

  /**
   * A type expression that that defines its range of possible values as being any one of the
   * possible values from a set of other type expressions.
   *
   * @param loc The location of the alternation type expression
   * @param of  The set of type expressions from which the value for this alternation may be chosen
   */
  case class Alternation(
    loc: Location,
    of: Seq[TypeExpression])
    extends TypeExpression

  /**
   * A definition that is a field of an aggregation type expressions. Fields associate an
   * identifier with a type expression.
   *
   * @param loc         The location of the field definition
   * @param id          The name of the field
   * @param typeEx      The type of the field
   * @param description An optional description of the field.
   */
  case class Field(
    loc: Location,
    id: Identifier,
    typeEx: TypeExpression,
    description: Option[Description] = None)
    extends Definition {}

  /**
   * A type expression that takes a set of named fields as its value.
   *
   * @param loc    The location of the aggregation definition
   * @param fields The fields of the aggregation
   */
  case class Aggregation(
    loc: Location,
    fields: Seq[Field] = Seq.empty[Field])
    extends TypeExpression with ContainerValue[Field] {
    lazy val contents: Seq[Field] = fields
  }

  /**
   * A type expressions that defines a mapping from a key to a value. The value of a mapping is
   * the set of mapped key -> value pairs, based on which keys have been provided values.
   *
   * @param loc  The location of the mapping type expression
   * @param from The type expression for the keys of the mapping
   * @param to   The type expression for the values of the mapping
   */
  case class Mapping(
    loc: Location,
    from: TypeExpression,
    to: TypeExpression)
    extends TypeExpression

  /**
   * A type expression that defines a set of integer values from a minimum value
   * to a maximum value, inclusively.
   *
   * @param loc The location of the range type expression
   * @param min The minimum value of the range
   * @param max The maximum value of the range
   */
  case class RangeType(
    loc: Location,
    min: LiteralInteger,
    max: LiteralInteger)
    extends TypeExpression

  /**
   * A type expression whose value is a reference to an entity.
   *
   * @param loc    The location of the reference type expression
   * @param entity The entity referenced by this type expression.
   */
  case class ReferenceType(
    loc: Location,
    entity: EntityRef)
    extends TypeExpression

  /**
   * A type expression that defines a string value constrained by a Java Regular Expression
   *
   * @param loc     The location of the pattern type expression
   * @param pattern The Java Regular Expression to which values of this type expression must obey.
   * @see https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html
   */
  case class Pattern(
    loc: Location,
    pattern: Seq[LiteralString])
    extends TypeExpression

  /**
   * A type expression for values that ensure a unique identifier for a specific entity.
   *
   * @param loc        The location of the unique identifier type expression
   * @param entityPath The path identifier of the entity type
   */
  case class UniqueId(
    loc: Location,
    entityPath: PathIdentifier)
    extends TypeExpression

  /**
   * A type expression for an aggregation type expression that is marked as being one of the four
   * message kinds.
   *
   * @param loc         The location of the message type expression
   * @param messageKind The kind of message defined
   * @param fields      The fields of the message's aggregation
   */
  case class MessageType(
    loc: Location,
    messageKind: MessageKind,
    fields: Seq[Field] = Seq.empty[Field])
    extends TypeExpression with EntityValue

  /**
   * Base class of all pre-defined type expressions
   */
  abstract class PredefinedType extends TypeExpression {
    def loc: Location

    def kind: String
  }

  object PredefinedType {
    final def unapply(preType: PredefinedType): Option[String] = Option(preType.kind)
  }

  /**
   * A type expression for values of arbitrary string type, possibly bounded by length.
   *
   * @param loc The location of the Strng type expression
   * @param min The minimum length of the string (default: 0)
   * @param max The maximum length of the string (default: MaxInt)
   */
  case class Strng(
    loc: Location,
    min: Option[LiteralInteger] = None,
    max: Option[LiteralInteger] = None)
    extends PredefinedType {
    override lazy val kind: String = "String"
  }

  /**
   * A predefined type expression for boolean values (true / false)
   *
   * @param loc The locatin of the Bool type expression
   */
  case class Bool(loc: Location) extends PredefinedType {
    override lazy val kind: String = "Boolean"
  }

  /**
   * A predefined type expression for an arbitrary number value
   *
   * @param loc The location of the number type expression
   */
  case class Number(loc: Location) extends PredefinedType {
    def kind: String = "Number"
  }

  /**
   * A predefined type expression for an integer value
   *
   * @param loc The locaiton of the integer type expression
   */
  case class Integer(loc: Location) extends PredefinedType {
    def kind: String = "Integer"
  }

  /**
   * A predefined type expression for a decimal value including IEEE
   * floating point syntax.
   *
   * @param loc The location of the decimal integer type expression
   */
  case class Decimal(loc: Location) extends PredefinedType {
    def kind: String = "Decimal"
  }

  /**
   * A predefined type expression for a real number value.
   *
   * @param loc The locaiton of the real number type expression
   */
  case class Real(loc: Location) extends PredefinedType {
    def kind: String = "Real"
  }

  /**
   * A predefined type expression for a calendar date.
   *
   * @param loc The location of the date type expression.
   */
  case class Date(loc: Location) extends PredefinedType {
    def kind: String = "Date"
  }

  /**
   * A predefined type expression for a clock time with hours, minutes, seconds.
   *
   * @param loc The location of the time type expression.
   */
  case class Time(loc: Location) extends PredefinedType {
    def kind: String = "Time"
  }

  /**
   * A predefined type expression for a calendar date and clock time combination.
   *
   * @param loc The location of the datetime type expression.
   */
  case class DateTime(loc: Location) extends PredefinedType {
    def kind: String = "DateTime"
  }

  /**
   * A predefined type expression for a timestamp that records the number of
   * milliseconds from the epoch.
   *
   * @param loc The location of the timestamp
   */
  case class TimeStamp(loc: Location) extends PredefinedType {
    def kind: String = "TimeStamp"
  }

  /**
   * A predefined type expression for a time duration that records the number of
   * milliseconds between two fixed points in time
   *
   * @param loc The location of the duration type expression
   */
  case class Duration(loc: Location) extends PredefinedType {
    def kind: String = "Duration"
  }

  /**
   * A predefined type expression for a universally unique identifier as defined
   * by the Java Virtual Machine.
   *
   * @param loc The location of the UUID type expression
   */
  case class UUID(loc: Location) extends PredefinedType {
    def kind: String = "UUID"
  }

  /**
   * A predefined type expression for a Uniform Resource Locator of a specific schema.
   *
   * @param loc    The location of the URL type expression
   * @param scheme The scheme to which the URL is constrained.
   */
  case class URL(loc: Location, scheme: Option[LiteralString] = None) extends PredefinedType {
    def kind: String = "URL"
  }

  /**
   * A predefined type expression for a location on earth given in latitude and longitude.
   *
   * @param loc The location of the LatLong type expression.
   */
  case class LatLong(loc: Location) extends PredefinedType {
    def kind: String = "LatLong"
  }

  /**
   * A predefined type expression for a type that can have no values
   *
   * @param loc The location of the nothing type expression.
   */
  case class Nothing(loc: Location) extends PredefinedType {
    def kind: String = "Nothing"
  }

  /**
   * A type definition which associates an identifier with a type expression.
   *
   * @param loc         The location of the type definition
   * @param id          The name of the type being defined
   * @param typ         The type expression of the type being defined
   * @param description An optional description of the type.
   */
  case class Type(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    description: Option[Description] = None)
    extends Definition with ContextDefinition with EntityDefinition with DomainDefinition {}

  // ///////////////////////////////// ///////////////////////// VALUE EXPRESSIONS

  /**
   * Base trait of all expressions
   */
  sealed trait Expression extends RiddlValue

  /**
   * Represents an arbitrary expression that is specified merely with a literal string. This
   * can't be easily processed downstream but provides the author with the ability to include
   * arbitrary ideas/concepts into an expression.
   * For example:
   * {{{
   *   +(42,"number of widgets in a wack-a-mole")
   * }}}
   * shows the use of an arbitrary expressions as the conditional to the "when" keyword
   *
   * @param loc  The location of the expression
   * @param what The arbitrary specification of the expression's calculation
   */
  case class ArbitraryExpression(loc: Location, what: LiteralString) extends Expression {
    override def format: String = what.format
  }

  /**
   * Represents the use of an arithmetic operator or well-known function call. The operator can
   * be simple like addition or subtraction or complicated like pow, sqrt, etc. There is no limit
   * on the number of operands but defining them shouldn't be necessary as they are
   * pre-determined by use of the name of the operator (e.g. pow takes two floating point
   * numbers, sqrt takes one.
   * @param loc The location of the operator
   * @param operator The name of the operator (+, -, sqrt, ...)
   * @param operands A list of expressions that correspond to the required operands for the operator
   */

  case class ArithmeticOperator(
    loc: Location, operator: String, operands: Seq[Expression]) extends Expression {
    override def format: String = operator + operands.mkString("(", ",", ")")
  }

  /**
   * Represents an expression that is merely a reference to some value, presumably an entity
   * state value.
   * @param loc The location of this expression
   * @param path The path to the value for this expression
   */
  case class ValueExpression(loc: Location, path: PathIdentifier) extends Condition {
    override def format: String = path.format
  }

  /**
   * A syntactic convenience for grouping another expression.
   * @param loc The location of the expression group
   * @param expression The expression that is grouped
   */
  case class GroupExpression(loc: Location, expression: Expression) extends Expression {
    override def format: String = s"(${expression.format})"
  }

  /**
   * The arguments of a [[FunctionCallExpression]] which is a mapping between an argument name and
   * the expression that provides the value for that argument.
   * @param args A mapping of Identifier to Expression to provide the arguments for the function
   *             call.
   */
  case class ArgList(args: ListMap[Identifier, Expression] = ListMap.empty[Identifier, Expression])
    extends RiddlNode {
    override def format: String = args.map { case (id, exp) =>
      id.format + "=" + exp.format
    }.mkString("(", ", ", ")")
  }

  /**
   * A RIDDL Function call. The only callable thing here is a function identified by its path
   * identifier with a matching set of arguments
   *
   * @param loc       The location of the function call expression
   * @param name      The path identifier of the RIDDL Function being called
   * @param arguments An [[ArgList]] to pass to the function.
   */
  case class FunctionCallExpression(loc: Location, name: PathIdentifier, arguments: ArgList)
    extends Expression with Condition {
    override def format: String = name.format + arguments.format
  }

  /**
   * An expression that is a literal constant integer value
   * @param loc The location of the integer value
   * @param n The number to use as the value of the expression
   */
  case class LiteralInteger(loc: Location, n: BigInt) extends Expression {
    override def format: String = n.toString()
  }

  /**
   * An expression that is a liberal constant decimal value
   * @param loc The loation of the decimal value
   * @param d The decimal number to use as the value of the expression
   */
  case class LiteralDecimal(loc: Location, d: BigDecimal) extends Expression {
    override def format: String = d.toString
  }

  ///////////////////////////////////////////////////////////// Conditional Expressions

  /**
   * Base trait for expressions that yield a boolean value (a condition)
   */
  sealed trait Condition extends Expression

  /**
   * A condition value for "true"
   * @param loc The location of this expression value
   */
  case class True(loc: Location) extends Condition {
    override def format: String = "true"
  }

  /**
   * A condition value for "false"
   * @param loc The location of this expression value
   */
  case class False(loc: Location) extends Condition {
    override def format: String = "false"
  }

  /**
   * Represents an arbitrary condition that is specified merely with a literal string. This
   * can't be easily processed downstream but provides the author with the ability to include
   * arbitrary ideas/concepts into a condition
   * For example:
   * {{{
   *   example foo { when "the timer has expired" }
   * }}}
   * shows the use of an arbitrary condition for the "when" part of a Gherkin example
   *
   * @param cond The arbitrary condition provided as a quoted string
   */
  case class ArbitraryCondition(cond: LiteralString) extends Condition {
    override def loc: Location = cond.loc

    override def format: String = cond.format
  }

  sealed trait Comparator extends RiddlNode

  final case object lt extends Comparator {
    override def format: String = "<"
  }

  final case object gt extends Comparator {
    override def format: String = ">"
  }

  final case object le extends Comparator {
    override def format: String = "<="
  }

  final case object ge extends Comparator {
    override def format: String = ">="
  }

  final case object eq extends Comparator {
    override def format: String = "=="
  }

  final case object ne extends Comparator {
    override def format: String = "!="
  }

  /**
   * Represents one of the six comparison operators
   *
   * @param loc   Location of the comparison
   * @param op    The comparison operator
   * @param expr1 The first operand in the comparison
   * @param expr2 The second operand in the comparison
   */
  case class Comparison(
    loc: Location,
    op: Comparator,
    expr1: Expression,
    expr2: Expression) extends Condition {
    override def format: String =
      op.format + Seq(expr1.format, expr2.format).mkString("(", ",", ")")
  }

  /**
   * Not condition
   *
   * @param loc   Location of the not condition
   * @param cond1 The condition being negated
   */
  case class NotCondition(loc: Location, cond1: Condition) extends Condition {
    override def format: String = "!(" + cond1 + ")"
  }

  /**
   * Base class for conditions with two operands
   */
  abstract class BinaryCondition extends Condition {
    def cond1: Condition

    def cond2: Condition

    override def format: String =
      Seq(cond1.format, cond2.format).mkString("(", ",", ")")
  }

  /**
   * And condition
   *
   * @param loc   Location of the and condition
   * @param cond1 First operand for the and condition
   * @param cond2 Second operand for the nad condition
   */
  case class AndCondition(loc: Location, cond1: Condition, cond2: Condition) extends
    BinaryCondition {
    override def format: String = "and" + super.format
  }

  /**
   * Or condition
   *
   * @param loc   Location of the or condition
   * @param cond1 First operand for the or condition
   * @param cond2 Second operand for the or condition
   */
  case class OrCondition(loc: Location, cond1: Condition, cond2: Condition) extends
    BinaryCondition {
    override def format: String = "or" + super.format
  }

  /**
   * Represents a condition expression that will be specified later and uses the ??? syntax to
   * represent that condition.
   *
   * @param loc The location of the undefined condition
   */
  case class UndefinedCondition(loc: Location) extends Condition {
    override def format: String = Terminals.Punctuation.undefined

    override def isEmpty: Boolean = true
  }

  ///////////////////////////////////////////////////////////// Actions

  /**
   * Base class for all actions. Actions are used in the "then" and "but" clauses of a Gherkin
   * example such as in the body of a [[Handler]]'s [[OnClause]] or in the definition of a
   * [[Function]]. The subclasses define different kinds of actions that can be used.
   */
  sealed trait Action extends DescribedValue

  /**
   * An action whose behavior is specified as a text string allowing extension to arbitrary
   * actions not otherwise handled by RIDDL's syntax.
   *
   * @param loc         The location where the action occurs in the source
   * @param what        The action to take (emitted as pseudo-code)
   * @param description An optional description of the action
   */
  case class ArbitraryAction(
    loc: Location, what: LiteralString,
    description: Option[Description]) extends Action {
    override def format: String = what.format
  }

  /** An action whose behavior is to set the value of a state field to some expression
   *
   * @param loc         The location where the action occurs int he source
   * @param target      The path identifier of the entity's state field that is to be set
   * @param value       An expression for the value to set the field to
   * @param description An optional description of the action
   */
  case class SetAction(
    loc: Location,
    target: PathIdentifier,
    value: Expression,
    description: Option[Description] = None)
    extends Action {
    override def format: String = {
      s"set ${target.format} to ${value.format}"
    }
  }

  /** A helper class for publishing messages that represents the construction of the message to
   * be sent.
   *
   * @param msg  A message reference that specifies the specific type of message to construct
   * @param args An argument list that should correspond to teh fields of the message
   */
  case class MessageConstructor(msg: MessageRef, args: ArgList = ArgList())
    extends RiddlNode {
    override def format: String = msg.format + {
      if (args.nonEmpty) {
        args.format
      } else {
        "()"
      }
    }
  }

  /** An action that publishes a message to a pipe
   *
   * @param loc         The location in the source of the publish action
   * @param msg         The constructed message to be published
   * @param pipe        The pipe onto which the message is published
   * @param description An optional description of the action
   */
  case class PublishAction(
    loc: Location,
    msg: MessageConstructor,
    pipe: PipeRef,
    description: Option[Description] = None)
    extends Action {
    override def format: String = s"publish ${msg.format} to ${pipe.format}"
  }

  /** An action that morphs the state of an entity to a new structure
   *
   * @param loc         The location of the morph action in the source
   * @param entity      The entity to be affected
   * @param state       The reference to the new state structure
   * @param description An optional description of this action
   */
  case class MorphAction(
    loc: Location,
    entity: EntityRef,
    state: StateRef,
    description: Option[Description] = None) extends Action {
    override def format: String = s"morph ${}"
  }

  /** An action that changes the behavior of an entity by making it use a new
   * handler for its messages; named for the "become" operation in Akka that
   * does the same for an actor.
   *
   * @param loc         The location in the source of the become action
   * @param entity      The entity whose behavior is to change
   * @param handler     The reference to the new handler for the entity
   * @param description An optional description of this action
   */
  case class BecomeAction(
    loc: Location,
    entity: EntityRef,
    handler: HandlerRef,
    description: Option[Description] = None
  ) extends Action {
    override def format: String = s"become ${}"
  }

  /**
   * An action that tells a message to an entity. This is very analagous to the tell operator in
   * Akka.
   *
   * @param loc         The location of the tell action
   * @param entity      The entity to which the message is directed
   * @param msg         A constructed message value to send to the entity, probably a command
   * @param description An optional description for this action
   */
  case class TellAction(
    loc: Location,
    entity: EntityRef,
    msg: MessageConstructor,
    description: Option[Description] = None)
    extends Action {
    override def format: String = s"tell ${entity.format} to ${msg.format}"
  }

  /**
   * An action that asks a query to an entity. This is very analogous to the ask operator in Akka.
   *
   * @param loc         The location of the ask action
   * @param entity      The entity to which the message is directed
   * @param msg         A constructed message value to send to the entity, probably a query
   * @param description An optional description of the action.
   */
  case class AskAction(
    loc: Location,
    entity: EntityRef,
    msg: MessageConstructor,
    description: Option[Description] = None) extends Action {
    override def format: String = s"ask ${entity.format} to ${msg.format}"
  }

  /**
   * An action that is a set of other actions.
   *
   * @param loc         The location of the compound action
   * @param actions     The actions in the compound group of actions
   * @param description An optional description for the action
   */
  case class CompoundAction(
    loc: Location,
    actions: Seq[Action],
    description: Option[Description] = None) extends Action {
    override def format: String = actions.mkString("{", ",", "}")
  }

  // ////////////////////////////////////////////////////////// Gherkin

  /**
   * Base class of any Gherkin value
   */
  sealed trait GherkinValue extends RiddlValue

  /**
   * Base class of one of the four Gherkin clauses (Given, When, Then, But)
   */
  sealed trait GherkinClause extends GherkinValue

  /**
   * A GherkinClause for the Given part of a Gherkin [[Example]]
   *
   * @param loc      The location of the Given clause
   * @param scenario The strings that define the scenario
   */
  case class GivenClause(loc: Location, scenario: Seq[LiteralString]) extends GherkinClause

  /**
   * A [[GherkinClause]] for the When part of a Gherkin [[Example]]
   *
   * @param loc       The location of the When clause
   * @param condition The condition expression that defines the trigger for the [[Example]]
   */
  case class WhenClause(loc: Location, condition: Condition) extends GherkinClause

  /**
   * A [[GherkinClause]] for the Then part of a Gherkin [[Example]]. This part specifies what
   * should be done if the [[WhenClause]] evaluates to true.
   *
   * @param loc    The location of the Then clause
   * @param action The action to be performed
   */
  case class ThenClause(loc: Location, action: Action) extends GherkinClause

  /**
   * A [[GherkinClause]] for the But part of a Gherkin [[Example]]. This part specifies what
   * should be done if the [[WhenClause]] evaluates to false.
   *
   * @param loc    The location of the But clause
   * @param action The action to be performed
   */
  case class ButClause(loc: Location, action: Action) extends GherkinClause

  /**
   * A Gherkin example. Examples have names, [[id]], and a sequence of each of the four kinds of
   * Gherkin clauses: [[GivenClause]], [[WhenClause]], [[ThenClause]], [[ButClause]]
   *
   * @see [[https://cucumber.io/docs/gherkin/reference/ The Gherkin Reference]]
   * @param loc         The location of the start of the example
   * @param id          The name of the example
   * @param givens      The list of Given/And statements
   * @param whens       The list of When/And statements
   * @param thens       The list of Then/And statements
   * @param buts        The List of But/And statements
   * @param description An optional description of the example
   */
  case class Example(
    loc: Location,
    id: Identifier,
    givens: Seq[GivenClause] = Seq.empty[GivenClause],
    whens: Seq[WhenClause] = Seq.empty[WhenClause],
    thens: Seq[ThenClause] = Seq.empty[ThenClause],
    buts: Seq[ButClause] = Seq.empty[ButClause],
    description: Option[Description] = None)
    extends ProcessorDefinition {
    override def isEmpty: Boolean = givens.isEmpty && whens.isEmpty && thens.isEmpty && buts.isEmpty
  }

  // ////////////////////////////////////////////////////////// Entities

  /**
   * Base trait of any value used in the definition of an entity
   */
  sealed trait EntityValue extends RiddlValue

  /**
   * Abstract base class of options for entities
   *
   * @param name the name of the option
   */
  sealed abstract class EntityOption(val name: String) extends EntityValue with OptionValue

  /**
   * An [[EntityOption]] that indicates that this entity should store its state in an event
   * sourced fashion.
   *
   * @param loc The location of the option.
   */
  case class EntityEventSourced(loc: Location) extends EntityOption("event sourced")

  /**
   * An [[EntityOption]] that indicates that this entity should store only the latest value
   * without using event sourcing. In other words, the history of changes is not stored.
   *
   * @param loc The location of the option
   */
  case class EntityValueOption(loc: Location) extends EntityOption("value")

  /**
   * An [[EntityOption]] that indicates that this entity should not persist its state and is only
   * available in transient memory. All entity values will be lost when the service is stopped.
   *
   * @param loc The location of the option.
   */
  case class EntityTransient(loc: Location) extends EntityOption("transient")

  /**
   * An [[EntityOption]] that indicates that this entity is an aggregate root entity through
   * which all commands and queries are sent on behalf of the aggregated entities.
   *
   * @param loc The location of the option
   */
  case class EntityAggregate(loc: Location) extends EntityOption("aggregate")

  /**
   * An [[EntityOption]] that indicates that this entity favors consistency over availability in
   * the CAP theorem.
   *
   * @param loc The location of the option.
   */
  case class EntityConsistent(loc: Location) extends EntityOption("consistent")

  /**
   * A [[EntityOption]] that indicates that this entity favors availability over consistency in
   * the CAP theorem.
   *
   * @param loc The location of the option.
   */
  case class EntityAvailable(loc: Location) extends EntityOption("available")

  /**
   * An [[EntityOption]] that indicates that this entity is intended to implement a finite state
   * machine.
   *
   * @param loc The location of the option.
   */
  case class EntityFiniteStateMachine(loc: Location) extends EntityOption("finite state machine")

  /**
   * An [[EntityOption]] that indicates the general kind of entity being defined. This option takes
   * a value which provides the kind.  Examples of useful kinds are "device", "actor", "concept",
   * "machine", and similar kinds of entities. This entity option may be used by downstream AST
   * processors, especially code generators.
   *
   * @param loc  The location of the entity kind option
   * @param args The argument to the option
   */
  case class EntityKind(loc: Location, override val args: Seq[LiteralString]) extends
    EntityOption("kind")

  /**
   * A reference to an entity
   *
   * @param loc The location of the entity reference
   * @param id  The path identifier of the referenced entity.
   */
  case class EntityRef(loc: Location, id: PathIdentifier) extends Reference[Entity] {
    override def format: String = s"${Keywords.entity} ${id.format}"
  }

  /**
   * A reference to a feature
   *
   * @param loc The location of the feature reference
   * @param id  The path identifier of the referenced feature
   */
  case class FeatureRef(loc: Location, id: PathIdentifier) extends Reference[Feature] {
    override def format: String = s"${Keywords.feature} ${id.format}"
  }

  /**
   * A feature of a bounded context specified as a set of Gherkin [[Example]]s
   *
   * @param loc         The location of the feature definition
   * @param id          The identifier that names the feature definition
   * @param examples    A set of example definitions to define the feature
   * @param description An optional description of the feature
   */
  case class Feature(
    loc: Location,
    id: Identifier,
    examples: Seq[Example] = Seq.empty[Example],
    description: Option[Description] = None)
    extends Container[Example] with EntityDefinition with ContextDefinition {
    lazy val contents: Seq[Example] = examples
  }

  /**
   * A reference to a function.
   *
   * @param loc The location of the function reference.
   * @param id  The path identifier of the referenced function.
   */
  case class FunctionRef(loc: Location, id: PathIdentifier) extends Reference[Function] {
    override def format: String = s"${Keywords.function} ${id.format}"
  }

  /**
   * A function definition which can be part of a bounded context or an entity.
   *
   * @param loc         The location of the function definition
   * @param id          The identifier that names the function
   * @param input       An optional type expression that names and types the fields of the input
   *                    of the
   *                    function
   * @param output      An optional type expression that names and types the fields of the output
   *                    of the
   *                    function
   * @param examples    The set of examples that define the behavior of the function.
   * @param description An optional description of the function.
   */
  case class Function(
    loc: Location,
    id: Identifier,
    input: Option[Aggregation] = None,
    output: Option[Aggregation] = None,
    examples: Seq[Example] = Seq.empty[Example],
    description: Option[Description] = None)
    extends Container[Example] with EntityDefinition {
    override lazy val contents: Seq[Example] = examples

    override def isEmpty: Boolean = examples.isEmpty && input.isEmpty && output.isEmpty
  }

  /**
   * An invariant expression that can be used in the definition of an entity. Invariants provide
   * conditional expressions that must be true at all times in the lifecycle of an entity.
   *
   * @param loc         The location of the invariant definition
   * @param id          The name of the invariant
   * @param expression  The conditional expression that must always be true.
   * @param description An optional description of the invariant.
   */
  case class Invariant(
    loc: Location,
    id: Identifier,
    expression: Condition,
    description: Option[Description] = None)
    extends EntityDefinition {
    override def isEmpty: Boolean = expression.isEmpty
  }

  /**
   * Defines the actions to be taken when a particular message is received by an entity.
   * [[OnClause]]s are used in the definition of a [[Handler]] with one for each kind of message
   * that handler deals with.
   *
   * @param loc         The location of the "on" clause
   * @param msg         A reference to the message type that is handled
   * @param examples    A set of examples that define the behavior when the [[msg]] is received.
   * @param description An optional description of the on clause.
   */
  case class OnClause(
    loc: Location,
    msg: MessageRef,
    examples: Seq[Example] = Seq.empty[Example],
    description: Option[Description] = None
  ) extends EntityValue with DescribedValue {
    override def isEmpty: Boolean = examples.isEmpty
  }

  /**
   * A named handler of messages (commands, events, queries) that bundles together a set of
   * [[OnClause]] definitions and by doing so defines the behavior of an entity. Note that
   * entities may define multiple handlers and switch between them to change how it responds
   * to messages over time or in response to changing conditions
   *
   * @param loc         The location of the handler definition
   * @param id          The name of the handler.
   * @param clauses     The set of [[OnClause]] definitions that define how the entity responds to
   *                    received messages.
   * @param description An optional description of the handler
   */
  case class Handler(
    loc: Location,
    id: Identifier,
    clauses: Seq[OnClause] = Seq.empty[OnClause],
    description: Option[Description] = None)
    extends EntityDefinition {
    override def isEmpty: Boolean = super.isEmpty && clauses.isEmpty
  }

  /**
   * A reference to a Handler
   *
   * @param loc The location of the handler reference
   * @param id  The path identifier of the referenced handler
   */
  case class HandlerRef(loc: Location, id: PathIdentifier) extends Reference[Handler] {
    override def format: String = s"${Keywords.handler} ${id.format}"
  }

  /**
   * Represents the state of an entity. The [[MorphAction]] can cause the state definition of an
   * entity to change.
   *
   * @param loc         The location of the state definition
   * @param id          The name of the state definition
   * @param typeEx      The aggregation that provides the field name and type expression
   *                    associations
   * @param description An optional description of the state.
   */
  case class State(
    loc: Location,
    id: Identifier,
    typeEx: Aggregation,
    description: Option[Description] = None)
    extends EntityDefinition with Container[Field] {

    override def contents: Seq[Field] = typeEx.fields
  }

  /**
   * A reference to an entity's state definition
   *
   * @param loc The location of the state reference
   * @param id  The path identifier of the referenced state definition
   */
  case class StateRef(loc: Location, id: PathIdentifier) extends Reference[State] {
    override def format: String = s"${Keywords.state} ${id.format}"
  }

  /** Definition of an Entity
   *
   * @param options
   * The options for the entity
   * @param loc
   * The location in the input
   * @param id
   * The name of the entity
   * @param states
   * The state values of the entity
   * @param types
   * Type definitions useful internally to the entity definition
   * @param handlers
   * A set of event handlers
   * @param functions
   * Utility functions defined for the entity
   * @param invariants
   * Invariant properties of the entity
    */
  case class Entity(
    loc: Location,
    id: Identifier,
    options: Seq[EntityOption] = Seq.empty[EntityOption],
    states: Seq[State] = Seq.empty[State],
    types: Seq[Type] = Seq.empty[Type],
    handlers: Seq[Handler] = Seq.empty[Handler],
    functions: Seq[Function] = Seq.empty[Function],
    invariants: Seq[Invariant] = Seq.empty[Invariant],
    description: Option[Description] = None)
    extends Container[EntityDefinition] with ContextDefinition with OptionsDef[EntityOption] {

    lazy val contents: Seq[EntityDefinition] = {
      (states ++ types ++ handlers ++ functions ++ invariants).toList
    }

    override def isEmpty: Boolean = contents.isEmpty && options.isEmpty
  }

  /**
   * The specification of a single adaptation based on message
   *
   * @param loc         The location of the adaptation definition
   * @param id          The name of the adaptation
   * @param event       The event that triggers the adaptation
   * @param command     The command that adapts the event to the bounded context
   * @param examples    Optional set of Gherkin [[Example]]s to define the adaptation
   * @param description Optional description of the adaptation.
   */
  case class Adaptation(
    loc: Location,
    id: Identifier,
    event: EventRef,
    command: CommandRef,
    examples: Seq[Example] = Seq.empty[Example],
    description: Option[Description] = None)
    extends AdaptorDefinition with ContainerValue[Example] {
    override def contents: Seq[Example] = examples
  }

  case class ActionAdaptation(
    loc: Location,
    id: Identifier,
    event: EventRef,
    action: Action
  )

  /**
   * Definition of an Adaptor. Adaptors are defined in Contexts to convert messages from another
   * bounded context. Adaptors translate incoming messages into corresponding messages using the
   * ubiquitous language of the defining bounded context. There should be one Adapter for each
   * external
   * Context
   *
   * @param loc         Location in the parsing input
   * @param id          Name of the adaptor
   * @param ref         A reference to the bounded context from which messages are adapted
   * @param adaptations A set of [[Adaptation]] definitions that indicate what to do when
   *                    messages occur.
   * @param description Optional description of the adaptor.
   */
  case class Adaptor(
    loc: Location,
    id: Identifier,
    ref: ContextRef,
    adaptations: Seq[Adaptation] = Seq.empty[Adaptation],
    description: Option[Description] = None)
    extends Container[Adaptation] with ContextDefinition {
    lazy val contents: Seq[Adaptation] = adaptations
  }

  /**
   * Base trait for all options a Context can have.
   */
  sealed trait ContextOption extends OptionValue

  /**
   * A context's "wrapper" option. This option suggests the bounded context is to be used as a
   * wrapper around an external system and is therefore at the boundary of the context map
   *
   * @param loc The location of the wrapper option
   */
  case class WrapperOption(loc: Location) extends ContextOption {
    def name: String = "wrapper"

  }

  /**
   * A context's "function" option that suggests
   *
   * @param loc The location of the function option
   */
  case class FunctionOption(loc: Location) extends ContextOption {
    def name: String = "function"
  }

  /**
   * A context's "gateway" option that suggests the bounded context is intended to be an
   * application gateway to the model. Gateway's provide authentication and authorization access
   * to external systems, usually user applications.
   *
   * @param loc The location of the gateway option
   *
   */
  case class GatewayOption(loc: Location) extends ContextOption {
    def name: String = "gateway"
  }

  /**
   * A reference to a bounded context
   *
   * @param loc The location of the reference
   * @param id  The path identifier for the referenced context
   */
  case class ContextRef(loc: Location, id: PathIdentifier) extends Reference[Context] {
    override def format: String = s"context ${id.format}"
  }

  /**
   * A bounded context definition. Bounded contexts provide a definitional boundary on the
   * language used to describe some aspect of a system. They imply a tightly integrated ecosystem
   * of one or more microservices that share a common purpose. Context can be used to house
   * entities, read side projections, sagas, adaptations to other contexts, apis, and etc.
   *
   * @param loc          The location of the bounded context definition
   * @param id           The name of the context
   * @param options      The options for the context
   * @param types        Types defined for the scope of this context
   * @param entities     Entities defined for the scope of this context
   * @param adaptors     Adaptors to messages from other contexts
   * @param sagas        Sagas with all-or-none semantics across various entities
   * @param features     Features specified for the context
   * @param interactions TBD
   * @param description  An optional description of the context
   */
  case class Context(
    loc: Location,
    id: Identifier,
    options: Seq[ContextOption] = Seq.empty[ContextOption],
    types: Seq[Type] = Seq.empty[Type],
    entities: Seq[Entity] = Seq.empty[Entity],
    adaptors: Seq[Adaptor] = Seq.empty[Adaptor],
    sagas: Seq[Saga] = Seq.empty[Saga],
    features: Seq[Feature] = Seq.empty[Feature],
    interactions: Seq[Interaction] = Seq.empty[Interaction],
    description: Option[Description] = None)
    extends Container[ContextDefinition] with DomainDefinition with OptionsDef[ContextOption] {
    lazy val contents: Seq[ContextDefinition] = types ++ entities ++ adaptors ++ sagas ++
      features ++ interactions

    override def isEmpty: Boolean = contents.isEmpty && options.isEmpty
  }

  /**
   * Base trait of any definition that occurs in the body of a plant
   */
  sealed trait PlantDefinition extends Definition

  /**
   * Definition of a pipe for data streaming purposes. Pipes are conduits through which data of a
   * particular type flows.
   *
   * @param loc          The location of the pipe definition
   * @param id           The name of the pipe
   * @param transmitType The type of data transmitted.
   * @param description  An optional description of the pipe.
   */
  case class Pipe(
    loc: Location,
    id: Identifier,
    transmitType: Option[TypeRef],
    description: Option[Description] = None)
    extends PlantDefinition

  /**
   * Base trait of definitions defined in a processor
   */
  trait ProcessorDefinition extends Definition

  /**
   * Base trait of an Inlet or Outlet definition
   */
  trait Streamlet extends ProcessorDefinition

  /**
   * A streamlet that supports input of data of a particular type.
   *
   * @param loc         The location of the Inlet definition
   * @param id          The name of the inlet
   * @param type_       The type of the data that is received from the inlet
   * @param description An optional description
   */
  case class Inlet(
    loc: Location,
    id: Identifier,
    type_ : TypeRef,
    description: Option[Description] = None)
    extends Streamlet

  /**
   * A streamlet that supports output of data of a particular type.
   *
   * @param loc         The location of the outlet definition
   * @param id          The name of the outlet
   * @param type_       The type expression for the kind of data put out
   * @param description An optional description of the outlet.
   */
  case class Outlet(
    loc: Location,
    id: Identifier,
    type_ : TypeRef,
    description: Option[Description] = None)
    extends Streamlet

  /**
   * A computing element for processing data from [[Inlet]]s to [[Outlet]]s. A processor's
   * processing is specified by Gherkin [[Example]]s
   *
   * @param loc         The location of the Processor definition
   * @param id          The name of the processor
   * @param inlets      The list of inlets that provide the data the processor needs
   * @param outlets     The list of outlets that the processor produces
   * @param examples    A set of examples that define the data processing
   * @param description An optional description of the processor
   */
  case class Processor(
    loc: Location,
    id: Identifier,
    inlets: Seq[Inlet],
    outlets: Seq[Outlet],
    examples: Seq[Example],
    description: Option[Description] = None)
    extends PlantDefinition with Container[ProcessorDefinition] {
    override def contents: Seq[ProcessorDefinition] = inlets ++ outlets ++ examples
  }

  /**
   * A reference to a pipe
   *
   * @param loc The location of the pipe reference
   * @param id  The path identifier for the referenced pipe.
   */
  case class PipeRef(loc: Location, id: PathIdentifier) extends Reference[Pipe] {
    override def format: String = s"pipe ${id.format}"
  }

  /**
   * Sealed base trait of references to [[Inlet]]s or [[Outlet]]s
   *
   * @tparam T The type of definition to which the references refers.
   */
  sealed trait StreamletRef[+T <: Definition] extends Reference[T]

  /**
   * A reference to an [[Inlet]]
   *
   * @param loc The location of the inlet reference
   * @param id  The path identifier of the referenced [[Inlet]]
   */
  case class InletRef(loc: Location, id: PathIdentifier) extends StreamletRef[Inlet] {
    override def format: String = s"inlet ${id.format}"
  }

  /**
   * A reference to an [[Outlet]]
   *
   * @param loc The location of the outlet reference
   * @param id  The path identifier of the referenced [[Outlet]]
   */
  case class OutletRef(loc: Location, id: PathIdentifier) extends StreamletRef[Outlet] {
    override def format: String = s"outlet ${id.format}"
  }

  /**
   * Sealed base trait for both kinds of Joint definitions
   */
  sealed trait Joint extends PlantDefinition

  /**
   * A joint that connects an [[Processor]]'s [[Inlet]] to a [[Pipe]].
   *
   * @param loc         The location of the InletJoint
   * @param id          The name of the inlet joint
   * @param inletRef    A reference to the inlet being connected
   * @param pipe        A reference to the pipe being connected
   * @param description An optional description of the joint
   */
  case class InletJoint(
    loc: Location,
    id: Identifier,
    inletRef: InletRef,
    pipe: PipeRef,
    description: Option[Description] = None)
    extends Joint

  /**
   * A joint that connects a [[Processor]]'s [[Outlet]] to a [[Pipe]].
   *
   * @param loc         The location of the OutletJoint
   * @param id          The name of the OutletJoint
   * @param outletRef   A reference to the outlet being connected
   * @param pipe        A reference to the pipe being connected
   * @param description An optional description of the OutletJoint
   */
  case class OutletJoint(
    loc: Location,
    id: Identifier,
    outletRef: OutletRef,
    pipe: PipeRef,
    description: Option[Description] = None)
    extends Joint

  /**
   * The definition of a plant which brings pipes, processors and joints together into a closed
   * system of data processing.
   *
   * @param loc         The location of the plant definition
   * @param id          The name of the plant
   * @param pipes       The set of pipes involved in the plant
   * @param processors  The set of processors involved in the plant.
   * @param inJoints    The InletJoints connecting pipes and processors
   * @param outJoints   The OutletJoints connecting pipes and processors
   * @param description An optional description of the plant
   */
  case class Plant(
    loc: Location,
    id: Identifier,
    pipes: Seq[Pipe] = Seq.empty[Pipe],
    processors: Seq[Processor] = Seq.empty[Processor],
    inJoints: Seq[InletJoint] = Seq.empty[InletJoint],
    outJoints: Seq[OutletJoint] = Seq.empty[OutletJoint],
    description: Option[Description] = None)
    extends Container[PlantDefinition] with DomainDefinition {
    lazy val contents: Seq[PlantDefinition] = pipes ++ processors ++ inJoints ++ outJoints
  }

  /**
   * The definition of one step in a saga with its undo step and example.
   *
   * @param loc         The location of the saga action definition
   * @param id          The name of the SagaAction
   * @param entity      A reference to the entity to which commands are directed
   * @param doCommand   The command to be done.
   * @param undoCommand The command that undoes [[doCommand]]
   * @param example     An list of examples for the intended behavior
   * @param description An optional description of the saga action
   */
  case class SagaAction(
    loc: Location,
    id: Identifier,
    entity: EntityRef,
    doCommand: CommandRef,
    undoCommand: CommandRef,
    example: Seq[Example],
    description: Option[Description] = None)
    extends Container[Example] {
    override def isEmpty: Boolean = example.isEmpty

    override def contents: Seq[Example] = example
  }

  /**
   * Base trait for all options applicable to a saga.
   */
  sealed trait SagaOption extends OptionValue

  /**
   * A [[SagaOption]] that indicates sequential (serial) execution of the saga actions.
   *
   * @param loc The location of the sequential option
   */
  case class SequentialOption(loc: Location) extends SagaOption {
    def name: String = "sequential"
  }

  /**
   * A [[SagaOption]] that indicates parallel execution of the saga actions.
   *
   * @param loc The location of the parallel option
   */
  case class ParallelOption(loc: Location) extends SagaOption {
    def name: String = "parallel"
  }

  /**
   * The definition of a Saga based on inputs, outputs, and the set of [[SagaAction]]s involved
   * in the saga. Sagas define a computing action based on a variety of related commands that
   * must all succeed atomically or have their effects undone.
   *
   * @param loc         The location of the Saga definition
   * @param id          The name of the saga
   * @param options     The options of the saga
   * @param input       A definition of the aggregate input values needed to invoke the saga, if
   *                    any.
   * @param output      A definition of the aggregate output values resulting from invoking the
   *                    saga,
   *                    if any.
   * @param sagaActions The set of [[SagaAction]]s that comprise the saga.
   * @param description An optional description of the saga.
   */
  case class Saga(
    loc: Location,
    id: Identifier,
    options: Seq[SagaOption] = Seq.empty[SagaOption],
    input: Option[Aggregation],
    output: Option[Aggregation],
    sagaActions: Seq[SagaAction] = Seq.empty[SagaAction],
    description: Option[Description] = None)
    extends Container[SagaAction] with ContextDefinition with OptionsDef[SagaOption] {
    lazy val contents: Seq[SagaAction] = sagaActions

    override def isEmpty: Boolean = super.isEmpty && options.isEmpty && input.isEmpty &&
      output.isEmpty
  }

  sealed trait InteractionOption extends OptionValue

  case class GatewayInteraction(loc: Location) extends InteractionOption {
    def name: String = "gateway"
  }

  sealed trait ActionDefinition extends Definition {
    def reactions: Seq[Reaction]
  }

  /** Definition of an Interaction
    *
    * Interactions define an exemplary interaction between the system being designed and other
    * actors. The basic ideas of an Interaction are much like UML Sequence Diagram.
    *
    * @param loc
    *   Where in the input the Scenario is defined
    * @param id
    *   The name of the scenario
    * @param actions
    *   The actions that constitute the interaction
    */
  case class Interaction(
    loc: Location,
    id: Identifier,
    options: Seq[InteractionOption] = Seq.empty[InteractionOption],
    actions: Seq[ActionDefinition] = Seq.empty[ActionDefinition],
    description: Option[Description] = None)
      extends Container[ActionDefinition]
      with DomainDefinition
      with ContextDefinition
      with OptionsDef[InteractionOption] {
    lazy val contents: Seq[ActionDefinition] = actions

    override def isEmpty: Boolean = super.isEmpty && options.isEmpty
  }

  sealed trait RoleOption extends RiddlValue

  case class HumanOption(loc: Location) extends RoleOption

  case class DeviceOption(loc: Location) extends RoleOption

  /** Used to capture reactions to actions. Actions include reactions in their definition to model
    * the precipitating reactions to the action.
    */
  case class Reaction(
    loc: Location,
    id: Identifier,
    entity: EntityRef,
    function: FunctionRef,
    arguments: Seq[LiteralString],
    description: Option[Description] = None)
      extends DescribedValue

  type Actions = Seq[ActionDefinition]

  sealed trait MessageOption extends OptionValue

  case class SynchOption(loc: Location) extends MessageOption {
    def name: String = "synch"
  }

  case class AsynchOption(loc: Location) extends MessageOption {
    def name: String = "async"
  }

  case class ReplyOption(loc: Location) extends MessageOption {
    def name: String = "reply"
  }

  /** An Interaction based on entity messaging between two entities in the system.
    *
    * @param options
    *   Options for the message
    * @param loc
    *   Where the message is located in the input
    * @param id
    *   The displayable text that describes the interaction
    * @param sender
    *   A reference to the entity sending the message
    * @param receiver
    *   A reference to the entity receiving the message
    * @param message
    *   A reference to the kind of message sent & received
   */
  case class MessageAction(
    loc: Location,
    id: Identifier,
    options: Seq[MessageOption] = Seq.empty[MessageOption],
    sender: EntityRef,
    receiver: EntityRef,
    message: MessageRef,
    reactions: Seq[Reaction],
    description: Option[Description] = None)
    extends ActionDefinition with OptionsDef[MessageOption]

  /**
   * A reference to a domain definition
   *
   * @param loc The location at which the domain definition occurs
   * @param id  The path identifier for the referenced domain.
   */
  case class DomainRef(loc: Location, id: PathIdentifier) extends Reference[Domain] {
    override def format: String = s"domain ${id.format}"
  }

  /**
   * The definition of a domain. Domains are the highest building block in RIDDL and may be
   * nested inside each other to form a hierarchy of domains. Generally, domains follow
   * hierarchical organization structure but other taxonomies and ontologies may be modelled with
   * domains too.
   *
   * @param loc          The location of the domain definition
   * @param id           The name of the domain
   * @param types        The types defined in the scope of the domain
   * @param contexts     The contexts defined in the scope of the domain
   * @param interactions TBD
   * @param plants       The plants defined in the scope of the domain
   * @param domains      Nested sub-domains within this domain
   * @param description  An optional description of the domain.
   */
  case class Domain(
    loc: Location,
    id: Identifier,
    types: Seq[Type] = Seq.empty[Type],
    contexts: Seq[Context] = Seq.empty[Context],
    interactions: Seq[Interaction] = Seq.empty[Interaction],
    plants: Seq[Plant] = Seq.empty[Plant],
    domains: Seq[Domain] = Seq.empty[Domain],
    description: Option[Description] = None)
    extends Container[DomainDefinition] with DomainDefinition {

    lazy val contents: Seq[DomainDefinition] =
      (domains ++ types.iterator ++ contexts ++ interactions ++ plants).toList
  }
}
