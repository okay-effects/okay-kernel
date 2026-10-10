package okay

import scala.annotation.unchecked.uncheckedVariance
import okay.kernel.{Fn, Handler, Monad, Op, Program, Steps}

/** an effect of a type parameter in a row: `Writer % String` */
infix type %[E[_], T] = E[T]

/** effects together in a row: `Writer % String + Async` */
infix type +[A, B] = A & B

/** `A ! R`: a program of value `A` needing the effects `R` — checked here, statically; at run time a kernel
 * `Program`, a recipe for any monad, called from any language */
infix type ![A, R] = Eff[R, A]

/** A PROGRAM OF SCALA 3: the kernel's `Program`, its effects in its type. `R` contravariant: a program needing fewer
 * effects is one needing more. Its nodes, a class each: written, one object a step, no closure — built in a monad,
 * one closure a bind */
sealed abstract class Eff[-R, +A] extends Program[A @uncheckedVariance]:
  def flatMap[R2, B](f: A => Eff[R2, B]): Eff[R & R2, B] = Eff.FlatMap(this, f.asInstanceOf[Any => Eff[Any, Any]])
  def map[B](f: A => B): Eff[R, B] = Eff.MapE(this, f.asInstanceOf[Any => Any])
  def andThen[R2, B](next: Eff[R2, B]): Eff[R & R2, B] = flatMap(_ => next)

object Eff:
  private final class PureE[A](a: A) extends Eff[Any, A]:
    def in(m: Monad): Any = m.pure(a)
  private final class FlatMap[R, A, B](p: Eff[R, A], f: Any => Eff[Any, Any]) extends Eff[R, B]:
    def in(m: Monad): Any = m.flatMap(p.in(m), a => f(a).in(m))
  private final class MapE[R, A, B](p: Eff[R, A], f: Any => Any) extends Eff[R, B]:
    def in(m: Monad): Any = m.map(p.in(m), a => f(a))
  private final class DelayE[R, A](p: () => Eff[R, A]) extends Eff[R, A]:
    def in(m: Monad): Any = m.delay(() => p().in(m))
  private final class PerformE[R, A](op: Op[A]) extends Eff[R, A]:
    def in(m: Monad): Any = m.perform(op)
  private final class HandleE[R, B](h: Handler, p: Program[?]) extends Eff[R, B]:
    def in(m: Monad): Any = m.handle(h, p)
  private final class From[R, A](p: Program[A]) extends Eff[R, A]:
    def in(m: Monad): Any = p.in(m)

  def pure[A](a: A): Eff[Any, A] = PureE(a)
  /** a program built when it is run: a tail call */
  def delay[R, A](p: => Eff[R, A]): Eff[R, A] = DelayE(() => p)
  /** an operation of an effect `E`, to the nearest handler of it */
  def perform[E, A](op: Op[A]): Eff[E, A] = PerformE(op)
  /** `p` under a handler of `E`: what is left of its effects, `R` */
  def handle[E, R, A, B](h: Handler, p: Eff[E & R, A]): Eff[R, B] = HandleE(h, p)
  /** a program of another language as one of Scala 3: its effects as said, `R` — checked at run time */
  def from[R, A](p: Program[A]): Eff[R, A] = p match
    case e: Eff[?, ?] => e.asInstanceOf[Eff[R, A]]
    case _ => From(p)
  /** a program needing nothing, run: by the machine of steps, or another monad */
  def run[A](p: Eff[Any, A], m: Monad = Steps): A = Program.run(p, m)
