package okay.kernel.std

import okay.kernel.{Effect, Fn, Op, Prog, Program}

// THE STANDARD EFFECTS' DATA'S MIRROR, for Scala.js and Scala Native: on the JVM `api`'s Java classes, these their
// signatures in Scala. An operation of value `Void` answered `null`

object Effects:
  def writer(w: String): Effect = Effect.of("Writer", w)
  def writer(w: Class[?]): Effect = writer(Effect.typeName(w))
  def throwsOf(e: String): Effect = Effect.of("Throws", e)
  def throwsOf(e: Class[?]): Effect = throwsOf(Effect.typeName(e))
  def state(s: String): Effect = Effect.of("State", s)
  def state(s: Class[?]): Effect = state(Effect.typeName(s))
  val ASYNC: Effect = Effect.of("Async")
  val CHOOSE: Effect = Effect.of("Choose")

final case class Tell[W](w: W, effect: Effect) extends Op[Void]
final case class Raise[E, A](error: E, effect: Effect) extends Op[A]
final case class Get[S](effect: Effect) extends Op[S]
final case class Put[S](s: S, effect: Effect) extends Op[Void]
final case class Among[A](as: java.util.List[A]) extends Op[A]:
  def effect: Effect = Effects.CHOOSE
final case class Await[A](register: okay.kernel.Sink[Callback[A]]) extends Op[A]:
  def effect: Effect = Effects.ASYNC
final case class Shift0[A, R](effect: Effect, f: Fn[Fn[A, Prog[R]], Program[R]]) extends Op[A]
final case class Shift[A, R](effect: Effect, f: Fn[Fn[A, Prog[R]], Program[R]]) extends Op[A]

trait Callback[A]:
  def ok(a: A): Unit
  def fail(e: Throwable): Unit

final class Logged[W, A] private (items: List[W], val value: A):
  def told(w: W): Logged[W, A] = Logged(w :: items, value)
  def log: java.util.List[W] =
    val l = java.util.ArrayList[W]()
    items.foreach(l.add)
    java.util.Collections.unmodifiableList(l)
  override def equals(o: Any): Boolean = o match
    case l: Logged[?, ?] => l.log == log && l.value == value
    case _ => false
  override def hashCode: Int = (log, value).##
  override def toString: String = s"Logged($log, $value)"
object Logged:
  def of[W, A](value: A): Logged[W, A] = Logged(Nil, value)

final case class Final[S, A](state: S, value: A)

sealed trait Result[E, A]
object Result:
  final case class Ok[E, A](value: A) extends Result[E, A]
  final case class Failed[E, A](error: E) extends Result[E, A]
  def ok[E, A](a: A): Result[E, A] = Ok(a)
  def failed[E, A](e: E): Result[E, A] = Failed(e)
