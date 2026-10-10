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

/** a value computed when asked */
trait Thunk[+A]:
  def get(): A

/** AN EFFECT: what a handler takes — by identity. `name` for errors and other languages' wire */
final class Effect(val name: String):
  override def toString: String = name

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
