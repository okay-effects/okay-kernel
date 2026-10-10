package okay

import scala.annotation.unchecked.uncheckedVariance
import okay.kernel.{Direct, Handler as KHandler, Monad, Op, Prog, Program, Steps}

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
  /** built in a monad that builds Scala's programs directly: its own nodes, no kernel `Prog` between */
  private[okay] def build(d: Direct): Any
  /** built in `m`: directly, if it can */
  final def in(m: Monad): Prog[A @uncheckedVariance] = m match
    case d: Direct => d.prog(build(d))
    case _ => via(m)
  /** built in `m` by the kernel's interface */
  protected def via(m: Monad): Prog[A @uncheckedVariance]
  def flatMap[R2, B](f: A => Eff[R2, B]): Eff[R & R2, B] = Eff.FlatMap(this, f)
  def map[B](f: A => B): Eff[R, B] = Eff.MapE(this, f)
  def andThen[R2, B](next: Eff[R2, B]): Eff[R & R2, B] = flatMap(_ => next)

object Eff:
  private final class PureE[A](a: A) extends Eff[Any, A]:
    protected def via(m: Monad): Prog[A] = m.pure(a)
    private[okay] def build(d: Direct): Any = d.directPure(a)
  private final class FlatMap[R, A, B](p: Eff[R, A], f: A => Eff[?, B]) extends Eff[R, B]:
    protected def via(m: Monad): Prog[B] = m.flatMap(p.in(m), a => f(a).in(m))
    private[okay] def build(d: Direct): Any = d.directFlatMap(p.build(d), a => f(a.asInstanceOf[A]).build(d))
  private final class MapE[R, A, B](p: Eff[R, A], f: A => B) extends Eff[R, B]:
    protected def via(m: Monad): Prog[B] = m.map(p.in(m), a => f(a))
    private[okay] def build(d: Direct): Any = d.directMap(p.build(d), f.asInstanceOf[Any => Any])
  private final class DelayE[R, A](p: () => Eff[R, A]) extends Eff[R, A]:
    protected def via(m: Monad): Prog[A] = m.delay(() => p().in(m))
    private[okay] def build(d: Direct): Any = d.directDelay(() => p().build(d))
  private final class PerformE[R, A](op: Op[A]) extends Eff[R, A]:
    protected def via(m: Monad): Prog[A] = m.perform(op)
    private[okay] def build(d: Direct): Any = d.directPerform(op)
  private final class HandleE[R, A, B](h: KHandler[A, B], p: Program[A]) extends Eff[R, B]:
    protected def via(m: Monad): Prog[B] = m.handle(h, p)
    private[okay] def build(d: Direct): Any = d.directHandle(h, p)
  private final class From[R, A](p: Program[A]) extends Eff[R, A]:
    protected def via(m: Monad): Prog[A] = p.in(m)
    private[okay] def build(d: Direct): Any = d.unprog(p.in(d))
  private final class Lift[R, A](p: Prog[A]) extends Eff[R, A]:
    protected def via(m: Monad): Prog[A] = p
    private[okay] def build(d: Direct): Any = d.unprog(p)

  def pure[A](a: A): Eff[Any, A] = PureE(a)
  /** a program built when it is run: a tail call */
  def delay[R, A](p: => Eff[R, A]): Eff[R, A] = DelayE(() => p)
  /** an operation of an effect `E`, to the nearest handler of it */
  def perform[E, A](op: Op[A]): Eff[E, A] = PerformE(op)
  /** `p` under a handler of `E`: what is left of its effects, `R` */
  def handle[E, R, A, B](h: KHandler[A, B], p: Eff[E & R, A]): Eff[R, B] = HandleE(h, p)
  /** a program of another language as one of Scala 3: its effects as said, `R` — checked at run time */
  def from[R, A](p: Program[A]): Eff[R, A] = p match
    case e: Eff[?, ?] => e.asInstanceOf[Eff[R, A]]
    case _ => From(p)
  /** a program of the monad being built in, as one of Scala 3: a clause's `k(x)` — run in that monad only */
  def lift[R, A](p: Prog[A]): Eff[R, A] = Lift(p)
  /** a program needing nothing, run: by the machine of steps, or another monad */
  def run[A](p: Eff[Any, A], m: Monad = Steps): A = Program.run(p, m)
