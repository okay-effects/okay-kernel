package okay.kernel

/**
 * THE KERNEL API: what every language and every platform works with, and every monad implements — typed through,
 * by plain generics only, so Scala 3, Scala 2 and Java read it alike.
 *
 * A program is a RECIPE, `Program[A]`: given a monad, it builds itself in it, a `Prog[A]` of that monad's. So a
 * program written in one language runs in any monad and is called from any language. Operations are data (`Op[X]`,
 * of value `X`), of an effect (`Effect`); a handler (`Handler[A, R]`) takes the operations of its effect, the nearest
 * in force, the body's value `A` to its answer `R`. A monad (`Monad`) is an implementation: its programs, its
 * machine, its speed — nothing a program sees changes from one monad to another.
 *
 * A `Prog[A]` is its monad's: built by it, given back to it only — a monad's type is not in it, for Java has no
 * type of a type constructor. No context function, union, type member, higher kind, nor `scala.Function1`.
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

/** AN OPERATION of an effect, as data, its value `X` */
trait Op[X]:
  def effect: Effect

/** A PROGRAM OF A MONAD: built by it, of value `A`, given back to it only */
trait Prog[A]

/** A HANDLER of an effect: the body's value `A` through `ret` to its answer `R`; an operation through its clause,
 * `k` the rest of the body up to the handler, the handler included (deep), any number of times, from anywhere —
 * typed by the operation's value. The clause runs outside the handler, in its place: an operation of the same effect
 * there goes to the next one out. Its programs built in the monad given */
trait Handler[A, R]:
  def effect: Effect
  def ret(a: A, m: Monad): Prog[R]
  def apply[X](op: Op[X], k: Fn[X, Prog[R]], m: Monad): Prog[R]

/** a TAIL-RESUMPTIVE handler: each clause `k(value(op))` — answered in place, no capture */
trait Answering[A, R] extends Handler[A, R]:
  def value[X](op: Op[X]): X
  def apply[X](op: Op[X], k: Fn[X, Prog[R]], m: Monad): Prog[R] = k(value(op))

/** A MONAD: an implementation of the kernel — its programs, and their run */
trait Monad:
  def pure[A](a: A): Prog[A]
  def flatMap[A, B](p: Prog[A], f: Fn[A, Prog[B]]): Prog[B]
  def map[A, B](p: Prog[A], f: Fn[A, B]): Prog[B] = flatMap(p, a => pure(f(a)))
  /** a program built when it is run: a tail call */
  def delay[A](p: Thunk[Prog[A]]): Prog[A]
  /** an operation, to the nearest handler of its effect in force */
  def perform[X](op: Op[X]): Prog[X]
  /** a program under a handler: built in this monad when the run enters it */
  def handle[A, R](h: Handler[A, R], body: Program[A]): Prog[R]
  /** the program's value; a failure, thrown — no handler for an operation among them */
  def run[A](p: Prog[A]): A

/** A PROGRAM: a recipe, built in any monad — what `A ! Es` is at run time, in every language */
trait Program[A]:
  def in(m: Monad): Prog[A]

object Program:
  /** a program run in a monad: its value */
  def run[A](p: Program[A], m: Monad): A = m.run(p.in(m))
