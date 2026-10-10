package okay.kernel

/**
 * THE KERNEL API: what every language and every platform works with, and every monad implements.
 *
 * A program is a RECIPE, `Program[A]`: given a monad, it builds itself in it. So a program written in one language
 * runs in any monad, and is called from any language: from Scala 3, Scala 2, Java, Scala.js, Scala Native alike.
 * Operations are data (`Op`), of an effect (`Effect`); a handler (`Handler`) takes the operations of its effect,
 * the nearest in force. A monad (`Monad`) is an implementation: its programs, its machine, its speed — nothing a
 * program sees changes from one monad to another.
 *
 * The signatures: plain generics and single-abstract-method traits only — no context function, union, type member,
 * higher kind, nor `scala.Function1`. Programs inside a monad are `Any`: each monad its own representation.
 */

/** a function: a lambda of Scala 3, Scala 2 or Java */
trait Fn[-A, +B]:
  def apply(a: A): B

/** a value taken, nothing given back: Java's `void` — a callback */
trait Sink[-A]:
  def accept(a: A): Unit

/** a value computed when asked */
trait Thunk[+A]:
  def get(): A

/** AN EFFECT: what a handler takes — by identity. `Effect(name)` a fresh one each call: a prompt, a delimiter of
 * its own (a reset's); `Effect.of(name, types)` the same one for the same name and types: `Writer % String` and
 * `Writer % Int` two effects, `Writer % String` one, in every language */
final class Effect private (val name: String):
  override def toString: String = name

object Effect:
  /** a fresh effect: no other is it */
  def apply(name: String): Effect = new Effect(name)

  private val known = scala.collection.mutable.HashMap.empty[String, Effect]

  /** the effect of this name over these types (their class names, primitives boxed): one for all who ask */
  @scala.annotation.varargs
  def of(name: String, types: String*): Effect =
    val key = if types.isEmpty then name else types.mkString(name + "[", ",", "]")
    known.synchronized(known.getOrElseUpdate(key, new Effect(key)))

  /** a type's name for `of`: its class's, a primitive's box's — Java's `int` and `Integer`, Scala's `Int`, one */
  def typeName(c: Class[?]): String =
    if !c.isPrimitive then c.getName
    else c.getName match
      case "int" => "java.lang.Integer"
      case "long" => "java.lang.Long"
      case "boolean" => "java.lang.Boolean"
      case "double" => "java.lang.Double"
      case "float" => "java.lang.Float"
      case "short" => "java.lang.Short"
      case "byte" => "java.lang.Byte"
      case "char" => "java.lang.Character"
      case "void" => "scala.runtime.BoxedUnit"
      case n => n

/** AN OPERATION of an effect, as data, its value `A` */
trait Op[A]:
  def effect: Effect

/** A HANDLER of an effect: the body's value through `ret`, an operation through its clause, `k` the rest of the body
 * up to the handler, the handler included (deep), any number of times. The clause runs outside the handler, in its
 * place: an operation of the same effect there goes to the next one out. Its programs built in the monad given */
trait Handler:
  def effect: Effect
  def ret(a: Any, m: Monad): Any
  def apply(op: Op[?], k: Fn[Any, Any], m: Monad): Any

/** a TAIL-RESUMPTIVE handler: each clause is `k(value(op))` — answered in place, no capture */
trait Answering extends Handler:
  def value(op: Op[?]): Any
  final def apply(op: Op[?], k: Fn[Any, Any], m: Monad): Any = k(value(op))

/** A MONAD: an implementation of the kernel — programs of its own (`Any`), and their run */
trait Monad:
  def pure(a: Any): Any
  def flatMap(p: Any, f: Fn[Any, Any]): Any
  def map(p: Any, f: Fn[Any, Any]): Any = flatMap(p, a => pure(f(a)))
  /** a program built when it is run: a tail call */
  def delay(p: Thunk[Any]): Any
  /** an operation, to the nearest handler of its effect in force */
  def perform(op: Op[?]): Any
  /** a program under a handler: built in this monad when the run enters it */
  def handle(h: Handler, body: Program[?]): Any
  /** the program's value; a failure, thrown — no handler for an operation among them */
  def run(p: Any): Any

/** A PROGRAM: a recipe, built in any monad — what `A ! Es` is at run time, in every language */
trait Program[A]:
  def in(m: Monad): Any

object Program:
  /** a program run in a monad: its value */
  def run[A](p: Program[A], m: Monad): A = m.run(p.in(m)).asInstanceOf[A]
